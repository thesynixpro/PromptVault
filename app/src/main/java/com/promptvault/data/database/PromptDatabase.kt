package com.promptvault.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PromptEntity::class,
        CategoryEntity::class,
        FolderEntity::class,
        SyncQueueEntity::class,
        DraftEntity::class,
        SearchHistoryEntity::class,
        ExportHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PromptDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun categoryDao(): CategoryDao
    abstract fun folderDao(): FolderDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun draftDao(): DraftDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun exportDao(): ExportDao

    companion object {
        @Volatile
        private var INSTANCE: PromptDatabase? = null

        fun get(context: Context): PromptDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                PromptDatabase::class.java,
                "promptvault.db"
            ).build().also { INSTANCE = it }
        }
    }
}
