package com.promptvault.telegram

import com.promptvault.domain.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMe(token: String): AppResult<TelegramUser> =
        call(token, "getMe", FormBody.Builder().build())

    suspend fun getChat(token: String, chatId: String): AppResult<TelegramChat> =
        call(token, "getChat", form("chat_id" to chatId))

    suspend fun getChatMember(
        token: String,
        chatId: String,
        userId: Long
    ): AppResult<TelegramChatMember> = call(
        token,
        "getChatMember",
        form("chat_id" to chatId, "user_id" to userId.toString())
    )

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String
    ): AppResult<TelegramMessage> = call(
        token,
        "sendMessage",
        form("chat_id" to chatId, "text" to text)
    )

    suspend fun sendDocument(
        token: String,
        chatId: String,
        file: File,
        caption: String
    ): AppResult<TelegramMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption.take(1024))
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody("text/plain; charset=utf-8".toMediaType())
                )
                .build()
            val request = request(token, "sendDocument", body)
            execute<T>(request)
        }.getOrElse { mapException(it) }
    }

    suspend fun editMessageText(
        token: String,
        chatId: String,
        messageId: Long,
        text: String
    ): AppResult<TelegramMessage> = call(
        token,
        "editMessageText",
        form(
            "chat_id" to chatId,
            "message_id" to messageId.toString(),
            "text" to text
        )
    )

    suspend fun deleteMessage(
        token: String,
        chatId: String,
        messageId: Long
    ): AppResult<Boolean> = call(
        token,
        "deleteMessage",
        form("chat_id" to chatId, "message_id" to messageId.toString())
    )

    private suspend inline fun <reified T : Any> call(
        token: String,
        method: String,
        body: okhttp3.RequestBody
    ): AppResult<T> = withContext(Dispatchers.IO) {
        runCatching { execute<T>(request(token, method, body)) }
            .getOrElse { mapException(it) }
    }

    private fun form(vararg pairs: Pair<String, String>): FormBody {
        val builder = FormBody.Builder()
        pairs.forEach { builder.add(it.first, it.second) }
        return builder.build()
    }

    private fun request(token: String, method: String, body: okhttp3.RequestBody): Request =
        Request.Builder()
            .url("https://api.telegram.org/bot$token/$method".toHttpUrl())
            .post(body)
            .build()

    private inline fun <reified T : Any> execute(request: Request): AppResult<T> {
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return parseError(body, response.code)
        val parsed = runCatching {
            json.decodeFromString<TelegramResponse<T>>(body)
        }.getOrElse { return parseError(body, response.code) }
        if (!parsed.ok || parsed.result == null) {
            return AppResult.Failure(
                parsed.description ?: "Telegram rejected the request",
                parsed.parameters?.retryAfter
            )
        }
        return AppResult.Success(parsed.result!!)
    }

    private fun parseError(body: String, code: Int): AppResult.Failure {
        val parsed = runCatching {
            json.decodeFromString<TelegramResponse<Unit>>(body)
        }.getOrNull()
        val description = parsed?.description?.takeIf { it.isNotBlank() }
            ?: "Telegram request failed (HTTP $code)"
        return AppResult.Failure(
            sanitize(description),
            parsed?.parameters?.retryAfter
        )
    }

    private fun mapException(error: Throwable): AppResult.Failure = when (error) {
        is IOException -> AppResult.Failure("Network unavailable. The item stays safely on this device.")
        is SerializationException -> AppResult.Failure("Telegram returned an unexpected response.")
        else -> AppResult.Failure("Could not complete the Telegram request.")
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("bot[0-9]+:[A-Za-z0-9_-]+"), "bot••••")
        .take(300)
}
