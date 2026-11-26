package com.promptvault.telegram

import com.promptvault.domain.AppResult
import com.promptvault.security.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TelegramRepository(
    private val api: TelegramApi,
    private val credentials: CredentialStore
) {
    private val _configured = MutableStateFlow(credentials.isConfigured())
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    val channelId: String? get() = credentials.getChannelId()
    val token: String? get() = credentials.getToken()
    val maskedToken: String get() = credentials.maskedToken()
    val secureStorageAvailable: Boolean get() = credentials.secureStorageAvailable

    suspend fun testConnection(token: String): AppResult<TelegramUser> = api.getMe(token.trim())

    suspend fun verifyChannel(
        token: String,
        channelId: String
    ): AppResult<ChannelVerification> {
        val cleanToken = token.trim()
        val cleanChannel = channelId.trim()
        if (cleanToken.isBlank() || cleanChannel.isBlank()) {
            return AppResult.Failure("Enter both a bot token and a channel ID.")
        }
        val me = when (val result = api.getMe(cleanToken)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val chat = when (val result = api.getChat(cleanToken, cleanChannel)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return AppResult.Failure(
                "Bot cannot access that channel: ${result.message}",
                result.retryAfterSeconds
            )
        }
        val member = when (
            val result = api.getChatMember(cleanToken, cleanChannel, me.id)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return AppResult.Failure(
                "Bot membership could not be verified: ${result.message}",
                result.retryAfterSeconds
            )
        }
        val admin = member.status == "administrator" || member.status == "creator"
        val verification = ChannelVerification(
            botUsername = me.username?.let { "@$it" } ?: me.firstName,
            chatTitle = chat.title ?: chat.username?.let { "@$it" } ?: cleanChannel,
            chatId = chat.id,
            isAdministrator = admin,
            canPost = member.canPostMessages != false,
            canEdit = member.canEditMessages != false,
            canDelete = member.canDeleteMessages != false
        )
        if (!verification.canBackup) {
            return AppResult.Failure(
                "Bot is in the channel but cannot post messages. Grant posting permission."
            )
        }
        return AppResult.Success(verification)
    }

    suspend fun saveConfiguration(token: String, channelId: String): AppResult<ChannelVerification> {
        if (!credentials.secureStorageAvailable) {
            return AppResult.Failure(
                "Secure credential storage is unavailable. Enable device encryption or unlock the keystore, then try again."
            )
        }
        val verification = verifyChannel(token, channelId)
        if (verification is AppResult.Success) {
            val saved = runCatching { credentials.saveTelegram(token, channelId) }
            if (saved.isFailure) {
                return AppResult.Failure(
                    saved.exceptionOrNull()?.message ?: "Credentials could not be stored securely."
                )
            }
            _configured.value = true
        }
        return verification
    }

    fun disconnect() {
        credentials.disconnect()
        _configured.value = false
    }

    suspend fun sendText(text: String): AppResult<UploadedBackup> {
        val token = token ?: return AppResult.Failure("Telegram is not configured.")
        val chat = channelId ?: return AppResult.Failure("Telegram channel is not configured.")
        return when (val result = api.sendMessage(token, chat, text)) {
            is AppResult.Success -> AppResult.Success(UploadedBackup(result.value.messageId))
            is AppResult.Failure -> result
        }
    }

    suspend fun editText(messageId: Long, text: String): AppResult<UploadedBackup> {
        val token = token ?: return AppResult.Failure("Telegram is not configured.")
        val chat = channelId ?: return AppResult.Failure("Telegram channel is not configured.")
        return when (val result = api.editMessageText(token, chat, messageId, text)) {
            is AppResult.Success -> AppResult.Success(UploadedBackup(result.value.messageId))
            is AppResult.Failure -> result
        }
    }

    suspend fun sendDocument(file: File, caption: String): AppResult<UploadedBackup> {
        val token = token ?: return AppResult.Failure("Telegram is not configured.")
        val chat = channelId ?: return AppResult.Failure("Telegram channel is not configured.")
        return when (val result = api.sendDocument(token, chat, file, caption)) {
            is AppResult.Success -> AppResult.Success(
                UploadedBackup(
                    messageId = result.value.messageId,
                    fileId = result.value.document?.fileId
                )
            )
            is AppResult.Failure -> result
        }
    }

    suspend fun deleteMessage(messageId: Long): AppResult<Boolean> {
        val token = token ?: return AppResult.Failure("Telegram is not configured.")
        val chat = channelId ?: return AppResult.Failure("Telegram channel is not configured.")
        return api.deleteMessage(token, chat, messageId)
    }
}
