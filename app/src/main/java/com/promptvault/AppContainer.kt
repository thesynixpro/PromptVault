package com.promptvault

import android.app.Application
import android.content.Context
import com.promptvault.data.database.PromptDatabase
import com.promptvault.data.preferences.SettingsDataStore
import com.promptvault.data.repository.PromptRepository
import com.promptvault.security.CredentialStore
import com.promptvault.telegram.TelegramApi
import com.promptvault.telegram.TelegramRepository
import com.promptvault.workers.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: PromptDatabase by lazy { PromptDatabase.get(appContext) }
    val settings: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val credentials: CredentialStore by lazy { CredentialStore(appContext) }
    val telegramApi: TelegramApi by lazy { TelegramApi() }
    val telegram: TelegramRepository by lazy { TelegramRepository(telegramApi, credentials) }
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
    val repository: PromptRepository by lazy {
        PromptRepository(database, settings, telegram, syncScheduler)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun configurePeriodicSync() {
        scope.launch {
            val current = settings.settings.first()
            syncScheduler.configurePeriodic(current.autoBackup, current.wifiOnly)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun from(context: Context): AppContainer = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AppContainer(context).also { INSTANCE = it }
        }
    }
}

class PromptVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.from(this)
        container.configurePeriodicSync()
    }
}
