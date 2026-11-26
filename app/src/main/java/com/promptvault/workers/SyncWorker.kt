package com.promptvault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.promptvault.AppContainer
import com.promptvault.data.database.PromptEntity
import com.promptvault.data.database.SyncQueueDao
import com.promptvault.data.database.SyncQueueEntity
import com.promptvault.domain.AppResult
import com.promptvault.domain.BackupFormatter
import com.promptvault.domain.ContentType
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SyncStatus
import com.promptvault.telegram.UploadedBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = AppContainer.from(applicationContext)
        val settings = container.settings.settings.first()
        if (!container.telegram.configured.value) {
            return@withContext Result.success()
        }
        if (!NetworkHelper.isOnline(applicationContext)) {
            return@withContext Result.retry()
        }
        if (settings.wifiOnly && !NetworkHelper.isUnmetered(applicationContext)) {
            return@withContext Result.retry()
        }

        val queueDao = container.database.syncQueueDao()
        // A task claimed just before a process death would otherwise stay SYNCING forever.
        queueDao.resetStaleSyncing(System.currentTimeMillis() - STALE_SYNCING_TIMEOUT_MS)
        // When automatic backup is off, only explicit cloud deletes are eligible. Query them
        // directly so they cannot be starved by non-delete rows in the limited PENDING window.
        val tasks = if (settings.autoBackup) {
            queueDao.getPending(MAX_TASKS_PER_RUN)
        } else {
            queueDao.getPendingDeletes(MAX_TASKS_PER_RUN)
        }
        val hasMoreWork = tasks.size >= MAX_TASKS_PER_RUN
        var shouldRetry = false
        for (task in tasks) {
            try {
                if (processTask(task, container)) shouldRetry = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                recordUnexpectedFailure(task, queueDao, container, error)
                shouldRetry = true
            }
        }
        if (hasMoreWork) container.syncScheduler.enqueueSoon(settings.wifiOnly)
        if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun processTask(
        task: SyncQueueEntity,
        container: AppContainer
    ): Boolean {
        val database = container.database
        val promptDao = database.promptDao()
        val queueDao = database.syncQueueDao()
        val now = System.currentTimeMillis()

        // Atomic claim prevents duplicate uploads when periodic and one-time work overlap.
        if (queueDao.claimPending(task.id, now) != 1) return false

        val item = promptDao.getById(task.promptId)?.let(::toDomain)
        if (item == null && task.operation != CLOUD_DELETE) {
            queueDao.delete(task)
            return false
        }
        item?.let { promptDao.setSyncStatus(it.id, SyncStatus.SYNCING.name) }

        var tempFile: File? = null
        var cleanupWarning: String? = null
        try {
            val useDocument = item != null &&
                task.operation != CLOUD_DELETE &&
                BackupFormatter.shouldUseDocument(item.content)
            if (useDocument) {
                tempFile = File(
                    applicationContext.cacheDir,
                    "promptvault_sync_${item!!.id}_v${item.localVersion}.txt"
                ).also { it.writeText(BackupFormatter.buildDocument(item), Charsets.UTF_8) }
            }

            val targetMessageId = task.targetMessageId ?: item?.telegramMessageId
            val outcome: AppResult<UploadedBackup> = when {
                task.operation == CLOUD_DELETE || (item == null && targetMessageId != null) -> {
                    when (val deleted = deleteCloudBackup(targetMessageId, container)) {
                        is AppResult.Success -> AppResult.Success(
                            UploadedBackup(messageId = targetMessageId ?: 0L)
                        )
                        is AppResult.Failure -> deleted
                    }
                }

                item == null -> AppResult.Failure("The local item no longer exists.")

                useDocument && task.operation == CLOUD_UPDATE && item.telegramMessageId != null -> {
                    when (val uploaded = container.telegram.sendDocument(tempFile!!, BackupFormatter.caption(item))) {
                        is AppResult.Success -> {
                            cleanupWarning = deletePreviousBackup(
                                item.telegramMessageId,
                                uploaded.value.messageId,
                                container
                            )
                            uploaded
                        }
                        is AppResult.Failure -> uploaded
                    }
                }

                useDocument -> container.telegram.sendDocument(tempFile!!, BackupFormatter.caption(item))

                // A document message cannot be edited as text: publish a new message and remove the old one.
                task.operation == CLOUD_UPDATE && !item.telegramFileId.isNullOrBlank() -> {
                    when (val uploaded = container.telegram.sendText(BackupFormatter.shortMessage(item))) {
                        is AppResult.Success -> {
                            cleanupWarning = deletePreviousBackup(
                                item.telegramMessageId,
                                uploaded.value.messageId,
                                container
                            )
                            uploaded
                        }
                        is AppResult.Failure -> uploaded
                    }
                }

                task.operation == CLOUD_UPDATE && item.telegramMessageId != null -> {
                    when (
                        val edited = container.telegram.editText(
                            item.telegramMessageId!!,
                            BackupFormatter.shortMessage(item)
                        )
                    ) {
                        is AppResult.Success -> edited
                        is AppResult.Failure -> {
                            when (val sent = container.telegram.sendText(BackupFormatter.shortMessage(item))) {
                                is AppResult.Success -> {
                                    cleanupWarning = deletePreviousBackup(
                                        item.telegramMessageId,
                                        sent.value.messageId,
                                        container
                                    )
                                    sent
                                }
                                is AppResult.Failure -> sent
                            }
                        }
                    }
                }

                else -> container.telegram.sendText(BackupFormatter.shortMessage(item))
            }

            return when (outcome) {
                is AppResult.Success -> {
                    val result = outcome.value
                    val fresh = queueDao.getById(task.id)
                    if (fresh != null) {
                        queueDao.update(
                            fresh.copy(
                                status = "DONE",
                                retryCount = 0,
                                lastAttempt = System.currentTimeMillis(),
                                errorMessage = cleanupWarning
                            )
                        )
                    }
                    val uploadedEntity = if (task.operation != CLOUD_DELETE && item != null) {
                        promptDao.getById(item.id)
                    } else {
                        null
                    }
                    val versionMatches = uploadedEntity != null && item != null &&
                        uploadedEntity.localVersion == item.localVersion
                    if (versionMatches) {
                        // Only trust the upload against the version that was actually sent.
                        val current = uploadedEntity!!
                        promptDao.updateCloudInfo(
                            id = current.id,
                            status = SyncStatus.SYNCED.name,
                            messageId = result.messageId,
                            fileId = result.fileId,
                            cloudVersion = current.localVersion
                        )
                    }
                    if (task.operation != CLOUD_DELETE && versionMatches) {
                        container.settings.setLastSuccessfulSync(System.currentTimeMillis())
                    }
                    false
                }

                is AppResult.Failure -> {
                    val fresh = queueDao.getById(task.id) ?: task
                    val retries = fresh.retryCount + 1
                    val exhausted = retries >= MAX_RETRIES
                    queueDao.update(
                        fresh.copy(
                            retryCount = retries,
                            status = if (exhausted) "FAILED" else "PENDING",
                            lastAttempt = System.currentTimeMillis(),
                            errorMessage = describeFailure(outcome)
                        )
                    )
                    if (task.operation != CLOUD_DELETE && item != null) {
                        promptDao.setSyncStatus(
                            item.id,
                            if (exhausted) SyncStatus.FAILED.name else SyncStatus.PENDING.name
                        )
                    }
                    !exhausted
                }
            }
        } finally {
            tempFile?.delete()
        }
    }

    private suspend fun deletePreviousBackup(
        previousMessageId: Long?,
        newMessageId: Long,
        container: AppContainer
    ): String? {
        if (previousMessageId == null || previousMessageId == newMessageId) return null
        return when (val result = container.telegram.deleteMessage(previousMessageId)) {
            is AppResult.Success -> null
            is AppResult.Failure -> "New backup published, but the previous cloud copy could not be removed: ${result.message}"
        }
    }

    private suspend fun recordUnexpectedFailure(
        task: SyncQueueEntity,
        queueDao: SyncQueueDao,
        container: AppContainer,
        error: Exception
    ) {
        val fresh = queueDao.getById(task.id) ?: task
        val retries = fresh.retryCount + 1
        val exhausted = retries >= MAX_RETRIES
        val message = error.message?.take(200) ?: "Unexpected sync error."
        queueDao.update(
            fresh.copy(
                retryCount = retries,
                status = if (exhausted) "FAILED" else "PENDING",
                lastAttempt = System.currentTimeMillis(),
                errorMessage = message
            )
        )
        if (task.operation != CLOUD_DELETE) {
            container.database.promptDao().setSyncStatus(
                task.promptId,
                if (exhausted) SyncStatus.FAILED.name else SyncStatus.PENDING.name
            )
        }
    }

    private suspend fun deleteCloudBackup(
        messageId: Long?,
        container: AppContainer
    ): AppResult<Boolean> {
        if (messageId == null) return AppResult.Success(true)
        return container.telegram.deleteMessage(messageId)
    }

    private fun describeFailure(failure: AppResult.Failure): String =
        if (failure.retryAfterSeconds != null) {
            "${failure.message} (retry after ${failure.retryAfterSeconds}s)"
        } else {
            failure.message
        }

    private fun toDomain(entity: PromptEntity): PromptItem = PromptItem(
        id = entity.id,
        title = entity.title,
        content = entity.content,
        contentType = runCatching { ContentType.valueOf(entity.contentType) }
            .getOrDefault(ContentType.TEXT),
        categoryId = entity.categoryId,
        folderId = entity.folderId,
        tags = entity.tags,
        isFavorite = entity.isFavorite,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        localVersion = entity.localVersion,
        cloudVersion = entity.cloudVersion,
        telegramMessageId = entity.telegramMessageId,
        telegramFileId = entity.telegramFileId,
        syncStatus = runCatching { SyncStatus.valueOf(entity.syncStatus) }
            .getOrDefault(SyncStatus.LOCAL_ONLY)
    )

    companion object {
        const val CLOUD_CREATE = "CLOUD_CREATE"
        const val CLOUD_UPDATE = "CLOUD_UPDATE"
        const val CLOUD_DELETE = "CLOUD_DELETE"
        private const val MAX_RETRIES = 5
        private const val MAX_TASKS_PER_RUN = 10
        // Longer than the worst-case Telegram call chain (send + cleanup retries) so a live
        // upload is never reclaimed by another worker instance.
        private const val STALE_SYNCING_TIMEOUT_MS = 10L * 60 * 1000
    }
}
