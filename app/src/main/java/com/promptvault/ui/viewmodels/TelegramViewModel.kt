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
    val verification: ChannelVerification? = null
)

class TelegramViewModel(container: AppContainer) : ViewModel() {
    private val telegram = container.telegram
    private val syncScheduler = container.syncScheduler

    private val _state = MutableStateFlow(
        TelegramUiState(
            configured = telegram.configured.value,
            maskedToken = telegram.maskedToken,
            savedChannel = telegram.channelId.orEmpty(),
            channelId = telegram.channelId.orEmpty()
        )
    )
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, status = "") }

    fun onChannelChange(value: String) = _state.update { it.copy(channelId = value, status = "") }

    fun refresh() = _state.update {
        it.copy(
            configured = telegram.configured.value,
            maskedToken = telegram.maskedToken,
            savedChannel = telegram.channelId.orEmpty(),
            channelId = it.channelId.ifBlank { telegram.channelId.orEmpty() }
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

    fun saveConfiguration(wifiOnly: Boolean = false) = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(isBusy = true, status = "Saving configuration…") }
        val result = telegram.saveConfiguration(current.token, current.channelId)
        _state.update {
            it.copy(
                isBusy = false,
                configured = result is AppResult.Success,
                maskedToken = telegram.maskedToken,
                savedChannel = telegram.channelId.orEmpty(),
                verification = (result as? AppResult.Success)?.value,
                status = when (result) {
                    is AppResult.Success -> "Configuration saved. Telegram backup is enabled."
                    is AppResult.Failure -> result.message
                }
            )
        }
        if (result is AppResult.Success) syncScheduler.enqueueSoon(wifiOnly)
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
