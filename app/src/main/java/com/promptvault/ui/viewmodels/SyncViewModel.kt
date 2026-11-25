package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.SyncTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val tasks: List<SyncTask> = emptyList(),
    val connected: Boolean = false,
    val wifiOnly: Boolean = false,
    val autoBackup: Boolean = true,
    val lastSuccessfulSync: Long = 0L
)

class SyncViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val scheduler = container.syncScheduler
    private val settings = container.settings.settings

    val uiState: StateFlow<SyncUiState> = combine(
        repository.observeSyncTasks(),
        container.telegram.configured,
        settings
    ) { tasks, connected, prefs ->
        SyncUiState(
            tasks = tasks,
            connected = connected,
            wifiOnly = prefs.wifiOnly,
            autoBackup = prefs.autoBackup,
            lastSuccessfulSync = prefs.lastSuccessfulSync
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun syncNow() = viewModelScope.launch {
        repository.queueAllForSync()
        scheduler.enqueueNow(uiState.value.wifiOnly)
    }

    fun retryFailed() = viewModelScope.launch {
        repository.retryFailedTasks()
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearSyncHistory()
    }

    fun syncItem(id: String) = viewModelScope.launch {
        repository.queueItemForSync(id)
        scheduler.enqueueNow(uiState.value.wifiOnly)
    }
}
