package com.promptvault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "promptvault_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.applicationContext.getSharedPreferences("promptvault_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun saveTelegram(token: String, channelId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_CHANNEL, channelId.trim())
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun getChannelId(): String? = prefs.getString(KEY_CHANNEL, null)?.takeIf { it.isNotBlank() }

    fun isConfigured(): Boolean = getToken() != null && getChannelId() != null

    fun maskedToken(): String {
        val token = getToken() ?: return "Not configured"
        val tail = token.takeLast(4)
        return "••••••••••••$tail"
    }

    fun disconnect() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_CHANNEL).apply()
    }

    private companion object {
        const val KEY_TOKEN = "telegram_bot_token"
        const val KEY_CHANNEL = "telegram_channel_id"
    }
}
