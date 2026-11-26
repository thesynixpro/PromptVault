package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.AppResult
import com.promptvault.telegram.ChannelVerification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelegramUiState(
    val token: String = "",
    val channelId: String = "",
    val configured: Boolean = false,
    val maskedToken: String = "Not configured",
    val savedChannel: String = "",
    val status: String = "",
    val isBusy: Boolean = false,
    val verification: ChannelVerification? = null,
    val secureStorageAvailable: Boolean = true
)

class TelegramViewModel(container: AppContainer) : ViewModel() {
    private val telegram = container.telegram
    private val repository = container.repository

    private val _state = MutableStateFlow(
        TelegramUiState(
            configured = telegram.configured.value,
            maskedToken = telegram.maskedToken,
            savedChannel = telegram.channelId.orEmpty(),
            channelId = telegram.channelId.orEmpty(),
            secureStorageAvailable = telegram.secureStorageAvailable
        )
    )
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            telegram.configured.collect { configured ->
                _state.update {
                    it.copy(
                        configured = configured,
                        maskedToken = telegram.maskedToken,
                        savedChannel = telegram.channelId.orEmpty(),
                        channelId = it.channelId.ifBlank { telegram.channelId.orEmpty() },
                        secureStorageAvailable = telegram.secureStorageAvailable
                    )
                }
            }
        }
    }

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, status = "") }

    fun onChannelChange(value: String) = _state.update { it.copy(channelId = value, status = "") }

    fun refresh() = _state.update {
        it.copy(
            configured = telegram.configured.value,
            maskedToken = telegram.maskedToken,
            savedChannel = telegram.channelId.orEmpty(),
            channelId = it.channelId.ifBlank { telegram.channelId.orEmpty() },
            secureStorageAvailable = telegram.secureStorageAvailable
        )
    }

    fun testConnection() = viewModelScope.launch {
        _state.update { it.copy(isBusy = true, status = "Testing bot token…") }
        val result = telegram.testConnection(_state.value.token)
        _state.update {
            it.copy(
                isBusy = false,
                status = when (result) {
                    is AppResult.Success -> "Connected as ${result.value.username?.let { name -> "@$name" } ?: result.value.firstName}."
                    is AppResult.Failure -> result.message
                }
            )
        }
    }

    fun verifyChannel() = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(isBusy = true, status = "Verifying channel access…") }
        val result = telegram.verifyChannel(current.token, current.channelId)
        _state.update {
            it.copy(
                isBusy = false,
                verification = (result as? AppResult.Success)?.value,
                status = when (result) {
                    is AppResult.Success -> "Verified: ${result.value.chatTitle}. Automatic backup is ready."
                    is AppResult.Failure -> result.message
                }
            )
        }
    }

    fun saveConfiguration() = viewModelScope.launch {
        val current = _state.value
        if (!current.secureStorageAvailable) {
            _state.update {
                it.copy(
                    status = "Secure credential storage is unavailable. Telegram was not configured.",
                    isBusy = false
                )
            }
            return@launch
        }
        _state.update { it.copy(isBusy = true, status = "Saving configuration…") }
        val result = telegram.saveConfiguration(current.token, current.channelId)
        var queueWarning: String? = null
        if (result is AppResult.Success) {
            // Existing local items were created before Telegram was configured, so they have no
            // queued work yet. Queue them now before starting the upload worker.
            queueWarning = runCatching { repository.queueAllForSync() }
                .exceptionOrNull()
                ?.let { error ->
                    "Connected, but queued backups could not be started: ${error.message}"
                }
        }
        _state.update {
            it.copy(
                isBusy = false,
                configured = result is AppResult.Success,
                maskedToken = telegram.maskedToken,
                savedChannel = telegram.channelId.orEmpty(),
                verification = (result as? AppResult.Success)?.value,
                status = when (result) {
                    is AppResult.Success -> queueWarning
                        ?: "Configuration saved. Telegram backup is enabled."
                    is AppResult.Failure -> result.message
                }
            )
        }
    }

    fun disconnect() {
        telegram.disconnect()
        _state.update {
            it.copy(
                configured = false,
                maskedToken = "Not configured",
                savedChannel = "",
                status = "Telegram disconnected. Local data is unchanged.",
                verification = null
            )
        }
    }
}
