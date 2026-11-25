package com.promptvault.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.promptvault.domain.SyncStatus

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentType: String,
    val categoryId: String? = null,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val localVersion: Int = 1,
    val cloudVersion: Int = 0,
    val telegramMessageId: Long? = null,
    val telegramFileId: String? = null,
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long = 0xFF1565C0,
    val icon: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val color: Long = 0xFF0097A7,
    val icon: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val promptId: String,
    val operation: String,
    val retryCount: Int = 0,
    val status: String = "PENDING",
    val lastAttempt: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val targetMessageId: Long? = null
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val itemId: String,
    val title: String,
    val content: String,
    val contentType: String,
    val categoryId: String?,
    val folderId: String?,
    val tags: List<String>,
    val isFavorite: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exports")
data class ExportHistoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class PromptWithNames(
    @Embedded val prompt: PromptEntity,
    val categoryName: String? = null,
    val folderName: String? = null
)

data class SyncTaskWithTitle(
    @Embedded val task: SyncQueueEntity,
    val itemTitle: String? = null
)
