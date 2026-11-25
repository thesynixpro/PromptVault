package com.promptvault.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.data.preferences.AppSettings
import com.promptvault.domain.ContentType
import com.promptvault.domain.SortMode
import com.promptvault.domain.ThemeMode
import com.promptvault.domain.ViewMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageInfo(
    val itemCount: Int = 0,
    val databaseBytes: Long = 0L
)

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val settingsStore = container.settings
    private val repository = container.repository
    private val scheduler = container.syncScheduler
    private val telegram = container.telegram

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    fun setTheme(value: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(value) }
    fun setViewMode(value: ViewMode) = viewModelScope.launch { settingsStore.setViewMode(value) }
    fun setSort(value: SortMode) = viewModelScope.launch { settingsStore.setSortMode(value) }
    fun setFontSize(value: Int) = viewModelScope.launch { settingsStore.setFontSize(value) }
    fun setLineSpacing(value: Int) = viewModelScope.launch { settingsStore.setLineSpacing(value) }
    fun setAutoSave(value: Boolean) = viewModelScope.launch { settingsStore.setAutoSave(value) }
    fun setDefaultType(value: ContentType) = viewModelScope.launch { settingsStore.setDefaultContentType(value) }
    fun setHideSensitive(value: Boolean) = viewModelScope.launch { settingsStore.setHideSensitive(value) }

    fun setAutoBackup(value: Boolean) = viewModelScope.launch {
        settingsStore.setAutoBackup(value)
        scheduler.configurePeriodic(value, settings.value.wifiOnly)
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        settingsStore.setWifiOnly(value)
        scheduler.configurePeriodic(settings.value.autoBackup, value)
    }

    fun disconnectTelegram() {
        telegram.disconnect()
    }

    suspend fun storageInfo(context: Context): StorageInfo {
        val stats = repository.getStats()
        val bytes = context.databaseList()
            .filter { it.endsWith(".db") || it.endsWith(".db-wal") || it.endsWith(".db-shm") }
            .sumOf { name -> context.getDatabasePath(name)?.length() ?: 0L }
        return StorageInfo(stats.total, bytes)
    }

    fun clearCache(context: Context) = viewModelScope.launch {
        runCatching {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }
}
