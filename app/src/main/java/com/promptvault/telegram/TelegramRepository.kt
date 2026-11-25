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
                "Bot cannot access that channel. Add it as an administrator first."
            )
        }
        val member = when (
            val result = api.getChatMember(cleanToken, cleanChannel, me.id)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return AppResult.Failure(
                "Bot is not a member/administrator of the channel."
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
        val verification = verifyChannel(token, channelId)
        if (verification is AppResult.Success) {
            credentials.saveTelegram(token, channelId)
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
