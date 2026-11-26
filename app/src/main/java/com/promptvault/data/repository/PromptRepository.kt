package com.promptvault.data.repository

import com.promptvault.data.database.CategoryEntity
import com.promptvault.data.database.DraftEntity
import com.promptvault.data.database.ExportHistoryEntity
import com.promptvault.data.database.FolderEntity
import com.promptvault.data.database.PromptDatabase
import com.promptvault.data.database.PromptEntity
import com.promptvault.data.database.PromptWithNames
import com.promptvault.data.database.SearchHistoryEntity
import com.promptvault.data.database.SyncQueueEntity
import com.promptvault.data.database.SyncTaskWithTitle
import com.promptvault.data.preferences.SettingsDataStore
import com.promptvault.domain.BackupFiles
import com.promptvault.domain.Category
import com.promptvault.domain.ContentType
import com.promptvault.domain.ExportRecord
import com.promptvault.domain.Folder
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SearchEngine
import com.promptvault.domain.SortMode
import com.promptvault.domain.SyncStatus
import com.promptvault.domain.SyncTask
import com.promptvault.telegram.TelegramRepository
import com.promptvault.workers.SyncScheduler
import com.promptvault.workers.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

data class ItemInput(
    val title: String,
    val content: String,
    val contentType: ContentType,
    val categoryId: String? = null,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false
)

data class ImportSummary(
    val imported: Int = 0,
    val skipped: Int = 0,
    val error: String? = null
)

data class VaultStats(
    val total: Int,
    val favorites: Int,
    val synced: Int,
    val pending: Int,
    val failed: Int,
    val folders: Int,
    val lastSuccessfulSync: Long
)

class PromptRepository(
    private val database: PromptDatabase,
    private val settings: SettingsDataStore,
    private val telegram: TelegramRepository,
    private val syncScheduler: SyncScheduler
) {
    private val promptDao get() = database.promptDao()
    private val categoryDao get() = database.categoryDao()
    private val folderDao get() = database.folderDao()
    private val queueDao get() = database.syncQueueDao()
    private val draftDao get() = database.draftDao()
    private val searchDao get() = database.searchHistoryDao()
    private val exportDao get() = database.exportDao()

    fun observeItems(
        filter: ItemFilter = ItemFilter.ALL,
        sort: SortMode = SortMode.RECENTLY_MODIFIED,
        query: String = ""
    ): Flow<List<PromptItem>> {
        // SearchEngine performs Unicode-aware, literal matching. Keeping one observable source also
        // avoids SQL LIKE wildcard surprises for %, _ and non-ASCII case folding.
        return promptDao.observeItems().map { rows ->
            val items = rows.map(::toDomain)
            SearchEngine.sort(SearchEngine.filter(items, filter, query), sort)
        }
    }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map(::toDomain) }

    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map(::toDomain) }

    fun observeSyncTasks(): Flow<List<SyncTask>> =
        queueDao.observeAll().map { list -> list.map(::toDomain) }

    fun observeSearchHistory(): Flow<List<String>> = searchDao.observeAll()

    fun observeExports(): Flow<List<ExportRecord>> =
        exportDao.observeAll().map { list -> list.map(::toDomain) }

    suspend fun getItem(id: String): PromptItem? =
        promptDao.getById(id)?.let { toDomain(it, "", "") }

    suspend fun createItem(input: ItemInput): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val queue = shouldQueue()
        val entity = PromptEntity(
            id = id,
            title = input.title.trim(),
            content = input.content,
            contentType = input.contentType.name,
            categoryId = input.categoryId,
            folderId = input.folderId,
            tags = input.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            isFavorite = input.isFavorite,
            createdAt = now,
            updatedAt = now,
            localVersion = 1,
            syncStatus = if (queue) SyncStatus.PENDING.name else SyncStatus.LOCAL_ONLY.name
        )
        promptDao.upsert(entity)
        if (queue) enqueueOperation(id, SyncWorker.CLOUD_CREATE)
        return id
    }

    suspend fun updateItem(id: String, input: ItemInput) {
        val existing = promptDao.getById(id) ?: return
        val queue = shouldQueue()
        // An edit is dirty even while Telegram is disconnected; otherwise a previously synced
        // item keeps SYNCED status and queueAllForSync() would never pick it up later.
        val dirtyStatus = if (existing.cloudVersion > 0) {
            SyncStatus.UPDATE_PENDING.name
        } else {
            SyncStatus.PENDING.name
        }
        promptDao.upsert(
            existing.copy(
                title = input.title.trim(),
                content = input.content,
                contentType = input.contentType.name,
                categoryId = input.categoryId,
                folderId = input.folderId,
                tags = input.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                isFavorite = input.isFavorite,
                updatedAt = System.currentTimeMillis(),
                localVersion = existing.localVersion + 1,
                syncStatus = dirtyStatus
            )
        )
        if (queue) {
            enqueueOperation(
                id,
                SyncWorker.CLOUD_UPDATE,
                existing.telegramMessageId
            )
        }
    }

    suspend fun toggleFavorite(id: String) {
        val item = promptDao.getById(id) ?: return
        val next = !item.isFavorite
        promptDao.setFavorite(id, next, System.currentTimeMillis())
        promptDao.setSyncStatus(
            id,
            if (item.cloudVersion > 0) SyncStatus.UPDATE_PENDING.name else SyncStatus.PENDING.name
        )
        if (shouldQueue()) {
            enqueueOperation(id, SyncWorker.CLOUD_UPDATE, item.telegramMessageId)
        }
    }

    suspend fun moveItem(id: String, folderId: String?) {
        val item = promptDao.getById(id) ?: return
        promptDao.moveToFolder(id, folderId, System.currentTimeMillis())
        promptDao.setSyncStatus(
            id,
            if (item.cloudVersion > 0) SyncStatus.UPDATE_PENDING.name else SyncStatus.PENDING.name
        )
        if (shouldQueue()) {
            enqueueOperation(id, SyncWorker.CLOUD_UPDATE, item.telegramMessageId)
        }
    }

    suspend fun duplicateItem(id: String): String? {
        val item = promptDao.getById(id) ?: return null
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val queue = shouldQueue()
        promptDao.upsert(
            item.copy(
                id = newId,
                title = "${item.title} (copy)",
                createdAt = now,
                updatedAt = now,
                localVersion = 1,
                cloudVersion = 0,
                telegramMessageId = null,
                telegramFileId = null,
                syncStatus = if (queue) SyncStatus.PENDING.name else SyncStatus.LOCAL_ONLY.name
            )
        )
        if (queue) enqueueOperation(newId, SyncWorker.CLOUD_CREATE)
        return newId
    }

    suspend fun deleteItem(id: String, requestCloudDelete: Boolean) {
        val item = promptDao.getById(id) ?: return
        val cloudDeleteTaskId = if (
            requestCloudDelete && telegram.configured.value && item.telegramMessageId != null
        ) {
            val taskId = UUID.randomUUID().toString()
            queueDao.upsert(
                SyncQueueEntity(
                    id = taskId,
                    promptId = id,
                    operation = SyncWorker.CLOUD_DELETE,
                    status = "PENDING",
                    targetMessageId = item.telegramMessageId
                )
            )
            taskId
        } else {
            null
        }
        if (cloudDeleteTaskId != null) {
            queueDao.deleteForPromptExcept(id, cloudDeleteTaskId)
        } else {
            queueDao.deleteForPrompt(id)
        }
        draftDao.delete(id)
        promptDao.delete(item)
        if (cloudDeleteTaskId != null) {
            syncScheduler.enqueueNow(settings.settings.first().wifiOnly)
        }
    }

    suspend fun saveDraft(input: ItemInput, itemId: String) {
        draftDao.upsert(
            DraftEntity(
                itemId = itemId,
                title = input.title,
                content = input.content,
                contentType = input.contentType.name,
                categoryId = input.categoryId,
                folderId = input.folderId,
                tags = input.tags,
                isFavorite = input.isFavorite,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getDraft(itemId: String): DraftEntity? = draftDao.get(itemId)

    suspend fun clearDraft(itemId: String) = draftDao.delete(itemId)

    suspend fun createCategory(name: String, color: Long, icon: String): String {
        val id = UUID.randomUUID().toString()
        categoryDao.upsert(
            CategoryEntity(id, name.trim(), color, icon, System.currentTimeMillis())
        )
        return id
    }

    suspend fun updateCategory(id: String, name: String, color: Long, icon: String) {
        categoryDao.update(id, name.trim(), color, icon)
        queueItemsForMetadataChange(promptDao.getByCategory(id))
    }

    suspend fun deleteCategory(id: String) {
        val affected = promptDao.getByCategory(id)
        categoryDao.clearFromItems(id)
        queueItemsForMetadataChange(affected)
        categoryDao.delete(categoryDao.getById(id) ?: return)
    }

    suspend fun createFolder(
        name: String,
        parentFolderId: String?,
        color: Long,
        icon: String
    ): String {
        val id = UUID.randomUUID().toString()
        folderDao.upsert(
            FolderEntity(id, name.trim(), parentFolderId, color, icon, System.currentTimeMillis())
        )
        return id
    }

    suspend fun updateFolder(id: String, name: String, color: Long, icon: String) {
        folderDao.update(id, name.trim(), color, icon)
        queueItemsForMetadataChange(promptDao.getByFolder(id))
    }

    suspend fun deleteFolder(id: String, deleteContents: Boolean) {
        val folder = folderDao.getById(id) ?: return
        val destination = folder.parentFolderId
        val children = folderDao.getChildren(id)
        children.forEach { child -> deleteFolderRecursive(child.id, deleteContents, destination) }
        val items = promptDao.getByFolder(id)
        if (deleteContents) {
            items.forEach { deleteItem(it.id, requestCloudDelete = false) }
        } else {
            items.forEach { item ->
                promptDao.moveToFolder(item.id, destination, System.currentTimeMillis())
                queueMetadataChange(item)
            }
        }
        folderDao.delete(folder)
    }

    private suspend fun deleteFolderRecursive(
        id: String,
        deleteContents: Boolean,
        destinationParentId: String?
    ) {
        val folder = folderDao.getById(id) ?: return
        folderDao.getChildren(id).forEach {
            deleteFolderRecursive(it.id, deleteContents, destinationParentId)
        }
        val items = promptDao.getByFolder(id)
        if (deleteContents) {
            items.forEach { deleteItem(it.id, requestCloudDelete = false) }
        } else {
            items.forEach { item ->
                promptDao.moveToFolder(item.id, destinationParentId, System.currentTimeMillis())
                queueMetadataChange(item)
            }
        }
        folderDao.getById(id)?.let { folderDao.delete(it) }
    }

    private suspend fun queueItemsForMetadataChange(items: List<PromptEntity>) {
        items.forEach { item ->
            promptDao.touch(item.id, System.currentTimeMillis())
            queueMetadataChange(item)
        }
    }

    private suspend fun queueMetadataChange(item: PromptEntity) {
        if (!shouldQueue()) return
        promptDao.setSyncStatus(item.id, SyncStatus.UPDATE_PENDING.name)
        enqueueOperation(item.id, SyncWorker.CLOUD_UPDATE, item.telegramMessageId)
    }

    suspend fun addSearchQuery(query: String) {
        val value = query.trim()
        if (value.length < 2) return
        searchDao.upsert(SearchHistoryEntity(value, System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = searchDao.clear()

    suspend fun recordExport(name: String, uri: String, mimeType: String, sizeBytes: Long) {
        exportDao.upsert(
            ExportHistoryEntity(
                UUID.randomUUID().toString(),
                name,
                uri,
                mimeType,
                sizeBytes,
                System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExport(record: ExportRecord) {
        exportDao.delete(
            ExportHistoryEntity(
                record.id, record.name, record.uri, record.mimeType, record.sizeBytes, record.createdAt
            )
        )
    }

    suspend fun createJsonBackup(selectedIds: Set<String>? = null): String {
        val items = promptDao.observeItems().first()
            .map(::toDomain)
            .filter { selectedIds == null || it.id in selectedIds }
            .map {
                BackupFiles.BackupItem(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    contentType = it.contentType.name,
                    categoryId = it.categoryId,
                    folderId = it.folderId,
                    categoryName = it.categoryName,
                    folderName = it.folderName,
                    tags = it.tags,
                    isFavorite = it.isFavorite,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    localVersion = it.localVersion
                )
            }
        val categories = categoryDao.observeAll().first().map {
            BackupFiles.BackupCategory(it.id, it.name, it.color, it.icon)
        }
        val folders = folderDao.observeAll().first().map {
            BackupFiles.BackupFolder(it.id, it.name, it.parentFolderId, it.color, it.icon)
        }
        return BackupFiles.encode(
            BackupFiles.Payload(
                exportedAt = System.currentTimeMillis(),
                items = items,
                categories = categories,
                folders = folders
            )
        )
    }

    suspend fun createTextBackup(id: String): String? {
        val item = getItem(id) ?: return null
        return buildString {
            append(item.title)
            append("\n\n")
            append(item.content)
        }
    }

    suspend fun importJson(text: String): ImportSummary {
        val payload = BackupFiles.decode(text)
            ?: return ImportSummary(error = "This is not a valid PromptVault JSON backup.")
        val existing = promptDao.observeItems().first().map { it.prompt }
        val existingIds = existing.map { it.id }.toSet()
        val signatures = existing.map { "${it.title.trim().lowercase()}\u0000${it.content}" }.toSet()
        val knownCategoryIds = categoryDao.observeAll().first().map { it.id }.toSet()
        val knownFolderIds = folderDao.observeAll().first().map { it.id }.toSet()

        payload.categories.filter { it.id !in knownCategoryIds }.forEach {
            categoryDao.upsert(
                CategoryEntity(it.id, it.name.ifBlank { "Imported" }, it.color, it.icon, System.currentTimeMillis())
            )
        }
        payload.folders.filter { it.id !in knownFolderIds }.forEach {
            folderDao.upsert(
                FolderEntity(
                    it.id,
                    it.name.ifBlank { "Imported" },
                    it.parentFolderId,
                    it.color,
                    it.icon,
                    System.currentTimeMillis()
                )
            )
        }

        var imported = 0
        var skipped = 0
        payload.items.forEach { backup ->
            val signature = "${backup.title.trim().lowercase()}\u0000${backup.content}"
            if (backup.id in existingIds || signature in signatures) {
                skipped++
                return@forEach
            }
            val now = System.currentTimeMillis()
            promptDao.upsert(
                PromptEntity(
                    id = backup.id.ifBlank { UUID.randomUUID().toString() },
                    title = backup.title.ifBlank { "Untitled" },
                    content = backup.content,
                    contentType = runCatching { ContentType.valueOf(backup.contentType) }
                        .getOrDefault(ContentType.TEXT).name,
                    categoryId = backup.categoryId?.takeIf { it in knownCategoryIds || payload.categories.any { c -> c.id == it } },
                    folderId = backup.folderId?.takeIf { it in knownFolderIds || payload.folders.any { f -> f.id == it } },
                    tags = backup.tags,
                    isFavorite = backup.isFavorite,
                    createdAt = backup.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                    localVersion = backup.localVersion.coerceAtLeast(1),
                    syncStatus = SyncStatus.LOCAL_ONLY.name
                )
            )
            imported++
        }
        return ImportSummary(imported, skipped)
    }

    suspend fun importText(text: String, title: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        promptDao.upsert(
            PromptEntity(
                id = id,
                title = title.trim().ifBlank { "Imported text" },
                content = text,
                contentType = ContentType.TEXT.name,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY.name
            )
        )
        return id
    }

    fun observeStats(): Flow<VaultStats> = combine(
        promptDao.observeItems(),
        queueDao.observePendingCount(),
        queueDao.observeFailedCount(),
        folderDao.observeAll(),
        settings.settings
    ) { rows, pending, failed, folders, prefs ->
        val items = rows.map { it.prompt }
        VaultStats(
            total = items.size,
            favorites = items.count { it.isFavorite },
            synced = items.count { it.syncStatus == SyncStatus.SYNCED.name },
            pending = pending,
            failed = failed,
            folders = folders.size,
            lastSuccessfulSync = prefs.lastSuccessfulSync
        )
    }

    suspend fun getStats(): VaultStats {
        val items = promptDao.observeItems().first().map { it.prompt }
        val prefs = settings.settings.first()
        return VaultStats(
            total = items.size,
            favorites = items.count { it.isFavorite },
            synced = items.count { it.syncStatus == SyncStatus.SYNCED.name },
            pending = queueDao.observePendingCount().first(),
            failed = queueDao.observeFailedCount().first(),
            folders = folderDao.observeAll().first().size,
            lastSuccessfulSync = prefs.lastSuccessfulSync
        )
    }

    suspend fun queueAllForSync() {
        // Explicit user request: it works even when automatic backup is switched off.
        if (!telegram.configured.value) return
        val items = promptDao.observeItems().first().map { it.prompt }
        items.filter { it.syncStatus != SyncStatus.SYNCED.name }.forEach { item ->
            promptDao.setSyncStatus(
                item.id,
                if (item.cloudVersion > 0) SyncStatus.UPDATE_PENDING.name else SyncStatus.PENDING.name
            )
            enqueueOperation(
                item.id,
                if (item.cloudVersion > 0) SyncWorker.CLOUD_UPDATE else SyncWorker.CLOUD_CREATE,
                item.telegramMessageId,
                explicit = true
            )
        }
        syncScheduler.enqueueNow(settings.settings.first().wifiOnly)
    }

    suspend fun clearSyncHistory() = queueDao.clearHistory()

    suspend fun queueItemForSync(id: String) {
        val item = promptDao.getById(id) ?: return
        if (!telegram.configured.value) return
        promptDao.setSyncStatus(
            id,
            if (item.cloudVersion > 0) SyncStatus.UPDATE_PENDING.name else SyncStatus.PENDING.name
        )
        enqueueOperation(
            id,
            if (item.cloudVersion > 0) SyncWorker.CLOUD_UPDATE else SyncWorker.CLOUD_CREATE,
            item.telegramMessageId,
            explicit = true
        )
    }

    suspend fun retryFailedTasks() {
        val failed = queueDao.observeAll().first().map { it.task }
            .filter { it.status == "FAILED" }
        failed.forEach {
            queueDao.update(
                it.copy(
                    status = "PENDING",
                    retryCount = 0,
                    errorMessage = null,
                    lastAttempt = null
                )
            )
        }
        if (failed.isNotEmpty()) syncScheduler.retryFailed(settings.settings.first().wifiOnly)
    }

    private suspend fun shouldQueue(): Boolean =
        settings.settings.first().autoBackup && telegram.configured.value

    private suspend fun enqueueOperation(
        promptId: String,
        operation: String,
        messageId: Long? = null,
        forceNewTask: Boolean = false,
        explicit: Boolean = false
    ) {
        if (!explicit && !shouldQueue()) return
        // A SYNCING task is already being uploaded with an older snapshot. Never rewrite it;
        // a separate task must represent the newer local version.
        val active = queueDao.getForPrompt(promptId).filter { it.status == "PENDING" }
        if (active.isNotEmpty() && !forceNewTask) {
            queueDao.update(
                active.first().copy(
                    operation = operation,
                    retryCount = 0,
                    errorMessage = null,
                    status = "PENDING",
                    targetMessageId = messageId
                )
            )
            return
        }
        queueDao.upsert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                promptId = promptId,
                operation = operation,
                status = "PENDING",
                targetMessageId = messageId
            )
        )
        syncScheduler.enqueueSoon(settings.settings.first().wifiOnly)
    }

    private fun toDomain(row: PromptWithNames): PromptItem = toDomain(
        row.prompt,
        row.categoryName.orEmpty(),
        row.folderName.orEmpty()
    )

    private fun toDomain(
        entity: PromptEntity,
        categoryName: String,
        folderName: String
    ): PromptItem = PromptItem(
        id = entity.id,
        title = entity.title,
        content = entity.content,
        contentType = runCatching { ContentType.valueOf(entity.contentType) }
            .getOrDefault(ContentType.TEXT),
        categoryId = entity.categoryId,
        folderId = entity.folderId,
        categoryName = categoryName,
        folderName = folderName,
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

    private fun toDomain(entity: CategoryEntity) = Category(
        entity.id, entity.name, entity.color, entity.icon, entity.createdAt
    )

    private fun toDomain(entity: FolderEntity) = Folder(
        entity.id, entity.name, entity.parentFolderId, entity.color, entity.icon, entity.createdAt
    )

    private fun toDomain(row: SyncTaskWithTitle) = SyncTask(
        id = row.task.id,
        promptId = row.task.promptId,
        operation = row.task.operation,
        retryCount = row.task.retryCount,
        status = row.task.status,
        lastAttempt = row.task.lastAttempt,
        errorMessage = row.task.errorMessage,
        createdAt = row.task.createdAt,
        itemTitle = row.itemTitle.orEmpty()
    )

    private fun toDomain(entity: ExportHistoryEntity) = ExportRecord(
        entity.id, entity.name, entity.uri, entity.mimeType, entity.sizeBytes, entity.createdAt
    )
}
