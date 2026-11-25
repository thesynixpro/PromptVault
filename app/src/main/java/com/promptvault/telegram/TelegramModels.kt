package com.promptvault.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean = false,
    val result: T? = null,
    val description: String? = null,
    val parameters: ResponseParameters? = null
)

@Serializable
data class ResponseParameters(
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("migrate_to_chat_id") val migrateToChatId: Long? = null
)

@Serializable
data class TelegramUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("first_name") val firstName: String = "",
    val username: String? = null
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String = "",
    val title: String? = null,
    val username: String? = null
)

@Serializable
data class TelegramChatMember(
    val status: String = "",
    val user: TelegramUser? = null,
    @SerialName("can_post_messages") val canPostMessages: Boolean? = null,
    @SerialName("can_edit_messages") val canEditMessages: Boolean? = null,
    @SerialName("can_delete_messages") val canDeleteMessages: Boolean? = null
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val chat: TelegramChat? = null,
    val date: Long = 0L,
    val text: String? = null,
    val document: TelegramDocument? = null
)

@Serializable
data class TelegramDocument(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_name") val fileName: String? = null
)

data class ChannelVerification(
    val botUsername: String,
    val chatTitle: String,
    val chatId: Long,
    val isAdministrator: Boolean,
    val canPost: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean
) {
    val canBackup: Boolean get() = isAdministrator && canPost
}

data class UploadedBackup(
    val messageId: Long,
    val fileId: String? = null
)
