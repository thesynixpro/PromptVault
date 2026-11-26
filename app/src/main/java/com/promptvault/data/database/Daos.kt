package com.promptvault.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Query(
        """
        SELECT p.*, c.name AS categoryName, f.name AS folderName
        FROM prompts p
        LEFT JOIN categories c ON p.categoryId = c.id
        LEFT JOIN folders f ON p.folderId = f.id
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeItems(): Flow<List<PromptWithNames>>

    @Query(
        """
        SELECT p.*, c.name AS categoryName, f.name AS folderName
        FROM prompts p
        LEFT JOIN categories c ON p.categoryId = c.id
        LEFT JOIN folders f ON p.folderId = f.id
        WHERE p.title LIKE '%' || :query || '%'
           OR p.content LIKE '%' || :query || '%'
           OR p.tags LIKE '%' || :query || '%'
           OR c.name LIKE '%' || :query || '%'
           OR f.name LIKE '%' || :query || '%'
        ORDER BY p.updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<PromptWithNames>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getById(id: String): PromptEntity?

    @Query("SELECT * FROM prompts WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: String): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE categoryId = :categoryId")
    suspend fun getByCategory(categoryId: String): List<PromptEntity>

    @Query("SELECT COUNT(*) FROM prompts")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(item: PromptEntity)

    @Update
    suspend fun update(item: PromptEntity)

    @Delete
    suspend fun delete(item: PromptEntity)

    @Query("UPDATE prompts SET isFavorite = :favorite, updatedAt = :updatedAt, localVersion = localVersion + 1 WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE prompts SET folderId = :folderId, updatedAt = :updatedAt, localVersion = localVersion + 1 WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE prompts SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)

    @Query("UPDATE prompts SET updatedAt = :updatedAt, localVersion = localVersion + 1 WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query(
        """
        UPDATE prompts
        SET syncStatus = 'SYNCED', cloudVersion = localVersion,
            telegramMessageId = :messageId, telegramFileId = :fileId
        WHERE id = :id
        """
    )
    suspend fun markSynced(id: String, messageId: Long?, fileId: String?)

    @Query("UPDATE prompts SET syncStatus = :status, telegramMessageId = :messageId, telegramFileId = :fileId, cloudVersion = :cloudVersion WHERE id = :id")
    suspend fun updateCloudInfo(
        id: String,
        status: String,
        messageId: Long?,
        fileId: String?,
        cloudVersion: Int
    )

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("UPDATE categories SET name = :name, color = :color, icon = :icon WHERE id = :id")
    suspend fun update(id: String, name: String, color: Long, icon: String)

    @Query("UPDATE prompts SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearFromItems(id: String)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId")
    suspend fun getChildren(parentId: String): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, color = :color, icon = :icon WHERE id = :id")
    suspend fun update(id: String, name: String, color: Long, icon: String)

    @Query("UPDATE folders SET parentFolderId = :parentId WHERE id = :id")
    suspend fun reparent(id: String, parentId: String?)
}

@Dao
interface SyncQueueDao {
    @Query(
        """
        SELECT q.*, p.title AS itemTitle FROM sync_queue q
        LEFT JOIN prompts p ON q.promptId = p.id
        ORDER BY q.createdAt DESC
        """
    )
    fun observeAll(): Flow<List<SyncTaskWithTitle>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 20): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: String): SyncQueueEntity?

    @Query(
        """
        UPDATE sync_queue SET status = 'SYNCING', lastAttempt = :now
        WHERE id = :id AND status = 'PENDING'
        """
    )
    suspend fun claimPending(id: String, now: Long): Int

    @Query(
        """
        UPDATE sync_queue SET status = 'PENDING', errorMessage = NULL
        WHERE status = 'SYNCING' AND lastAttempt < :cutoff
        """
    )
    suspend fun resetStaleSyncing(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'SYNCING') AND promptId = :promptId")
    suspend fun countActiveForPrompt(promptId: String): Int

    @Query("SELECT * FROM sync_queue WHERE promptId = :promptId ORDER BY createdAt DESC")
    suspend fun getForPrompt(promptId: String): List<SyncQueueEntity>

    @Upsert
    suspend fun upsert(task: SyncQueueEntity)

    @Update
    suspend fun update(task: SyncQueueEntity)

    @Delete
    suspend fun delete(task: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE promptId = :promptId")
    suspend fun deleteForPrompt(promptId: String)

    @Query("DELETE FROM sync_queue WHERE promptId = :promptId AND id != :keepTaskId")
    suspend fun deleteForPromptExcept(promptId: String, keepTaskId: String)

    @Query("DELETE FROM sync_queue")
    suspend fun clearHistory()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE itemId = :itemId")
    suspend fun get(itemId: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("DELETE FROM drafts")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT query FROM search_history ORDER BY createdAt DESC LIMIT 20")
    fun observeAll(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM exports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportHistoryEntity>>

    @Upsert
    suspend fun upsert(record: ExportHistoryEntity)

    @Delete
    suspend fun delete(record: ExportHistoryEntity)
}
