package com.promptvault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.promptvault.AppContainer
import com.promptvault.data.database.PromptEntity
import com.promptvault.data.database.SyncQueueEntity
import com.promptvault.domain.AppResult
import com.promptvault.domain.BackupFormatter
import com.promptvault.domain.ContentType
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SyncStatus
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
        if (!settings.autoBackup || !container.telegram.configured.value) {
            return@withContext Result.success()
        }
        if (!NetworkHelper.isOnline(applicationContext)) {
            return@withContext Result.retry()
        }
        if (settings.wifiOnly && !NetworkHelper.isUnmetered(applicationContext)) {
            return@withContext Result.retry()
        }

        val tasks = container.database.syncQueueDao().getPending(MAX_TASKS_PER_RUN)
        var shouldRetry = false
        for (task in tasks) {
            if (processTask(task, container)) shouldRetry = true
        }
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
        val item = promptDao.getById(task.promptId)?.let(::toDomain)
        if (item == null && task.operation != CLOUD_DELETE) {
            queueDao.delete(task)
            return false
        }

        queueDao.update(task.copy(status = "SYNCING", lastAttempt = now))
        item?.let { promptDao.setSyncStatus(it.id, SyncStatus.SYNCING.name) }

        val tempFile: File? = if (
            item != null &&
            task.operation != CLOUD_DELETE &&
            BackupFormatter.shouldUseDocument(item.content)
        ) {
            File(applicationContext.cacheDir, "promptvault_${item.id}_v${item.localVersion}.txt").also {
                it.writeText(BackupFormatter.buildDocument(item), Charsets.UTF_8)
            }
        } else null

        try {
            val outcome = when {
                task.operation == CLOUD_DELETE -> deleteCloudBackup(
                    task.targetMessageId ?: item?.telegramMessageId,
                    container
                )
                item != null && tempFile != null -> container.telegram.sendDocument(
                    tempFile,
                    BackupFormatter.caption(item)
                )
                item != null && task.operation == CLOUD_UPDATE && item.telegramMessageId != null ->
                    container.telegram.editText(
                        item.telegramMessageId!!,
                        BackupFormatter.shortMessage(item)
                    )
                item != null -> container.telegram.sendText(BackupFormatter.shortMessage(item))
                else -> AppResult.Failure("The local item no longer exists.")
            }

            return when (outcome) {
                is AppResult.Success -> {
                    queueDao.update(
                        task.copy(status = "DONE", lastAttempt = System.currentTimeMillis(), errorMessage = null)
                    )
                    if (task.operation != CLOUD_DELETE && item != null) {
                        val result = outcome.value as com.promptvault.telegram.UploadedBackup
                        promptDao.updateCloudInfo(
                            id = item.id,
                            status = SyncStatus.SYNCED.name,
                            messageId = result.messageId,
                            fileId = result.fileId,
                            cloudVersion = item.localVersion
                        )
                    }
                    container.settings.setLastSuccessfulSync(System.currentTimeMillis())
                    false
                }

                is AppResult.Failure -> {
                    val retries = task.retryCount + 1
                    val exhausted = retries >= MAX_RETRIES
                    queueDao.update(
                        task.copy(
                            retryCount = retries,
                            status = if (exhausted) "FAILED" else "PENDING",
                            lastAttempt = System.currentTimeMillis(),
                            errorMessage = outcome.message
                        )
                    )
                    if (task.operation != CLOUD_DELETE && item != null) {
                        promptDao.setSyncStatus(
                            item.id,
                            if (exhausted) SyncStatus.FAILED.name else SyncStatus.PENDING.name
                        )
                    }
                    true
                }
            }
        } finally {
            tempFile?.delete()
        }
    }

    private suspend fun deleteCloudBackup(
        messageId: Long?,
        container: AppContainer
    ): AppResult<Boolean> {
        if (messageId == null) return AppResult.Success(true)
        return container.telegram.deleteMessage(messageId)
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
    }
}
