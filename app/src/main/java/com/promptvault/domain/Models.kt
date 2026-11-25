package com.promptvault.domain

enum class ContentType {
    PROMPT,
    TEXT,
    CODE
}

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    UPDATE_PENDING
}

enum class ItemFilter {
    RECENT,
    ALL,
    PROMPTS,
    TEXT,
    CODE,
    FAVORITES,
    FOLDERS
}

enum class SortMode {
    NEWEST,
    OLDEST,
    RECENTLY_MODIFIED,
    ALPHABETICAL
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ViewMode {
    LIST,
    GRID
}

data class PromptItem(
    val id: String,
    val title: String,
    val content: String,
    val contentType: ContentType,
    val categoryId: String? = null,
    val folderId: String? = null,
    val categoryName: String = "",
    val folderName: String = "",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val localVersion: Int = 1,
    val cloudVersion: Int = 0,
    val telegramMessageId: Long? = null,
    val telegramFileId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

data class Category(
    val id: String,
    val name: String,
    val color: Long,
    val icon: String,
    val createdAt: Long
)

data class Folder(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val color: Long,
    val icon: String,
    val createdAt: Long
)

data class SyncTask(
    val id: String,
    val promptId: String,
    val operation: String,
    val retryCount: Int,
    val status: String,
    val lastAttempt: Long?,
    val errorMessage: String?,
    val createdAt: Long,
    val itemTitle: String = ""
)

data class ExportRecord(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long
)

sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val retryAfterSeconds: Int? = null) : AppResult<Nothing>
}
