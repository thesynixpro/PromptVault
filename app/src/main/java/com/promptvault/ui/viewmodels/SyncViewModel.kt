package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.SyncTask
import kotlinx.coroutines.flow.MutableStateFlow
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
    val lastSuccessfulSync: Long = 0L,
    val isSyncing: Boolean = false,
    val message: String? = null
)

private data class SyncActionState(
    val isSyncing: Boolean = false,
    val message: String? = null
)

class SyncViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val configured = container.telegram.configured
    private val settingsFlow = container.settings.settings
    private val actionState = MutableStateFlow(SyncActionState())

    val uiState: StateFlow<SyncUiState> = combine(
        repository.observeSyncTasks(),
        configured,
        settingsFlow,
        actionState
    ) { tasks, connected, prefs, action ->
        SyncUiState(
            tasks = tasks,
            connected = connected,
            wifiOnly = prefs.wifiOnly,
            autoBackup = prefs.autoBackup,
            lastSuccessfulSync = prefs.lastSuccessfulSync,
            isSyncing = action.isSyncing,
            message = action.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun syncNow() = viewModelScope.launch {
        actionState.value = SyncActionState(isSyncing = true)
        try {
            repository.queueAllForSync()
            actionState.value = SyncActionState(
                message = if (configured.value) {
                    "Sync requested. Pending items will upload in the background."
                } else {
                    "Telegram is not connected. No cloud upload was started."
                }
            )
        } catch (error: Exception) {
            actionState.value = SyncActionState(
                message = error.message ?: "Sync could not be started."
            )
        }
    }

    fun retryFailed() = viewModelScope.launch {
        actionState.value = SyncActionState(isSyncing = true)
        try {
            repository.retryFailedTasks()
            actionState.value = SyncActionState(message = "Failed tasks queued for another attempt.")
        } catch (error: Exception) {
            actionState.value = SyncActionState(
                message = error.message ?: "Failed tasks could not be retried."
            )
        }
    }

    fun clearHistory() = viewModelScope.launch {
        try {
            repository.clearSyncHistory()
            actionState.value = SyncActionState(message = "Sync history cleared.")
        } catch (error: Exception) {
            actionState.value = SyncActionState(
                message = error.message ?: "Sync history could not be cleared."
            )
        }
    }

    fun syncItem(id: String) = viewModelScope.launch {
        try {
            repository.queueItemForSync(id)
            actionState.value = SyncActionState(message = "Item queued for backup.")
        } catch (error: Exception) {
            actionState.value = SyncActionState(
                message = error.message ?: "The item could not be queued."
            )
        }
    }

    fun clearMessage() {
        actionState.value = actionState.value.copy(message = null)
    }
}
