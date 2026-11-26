package com.promptvault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val encryptedPrefs: SharedPreferences?
    private val legacyPrefs: SharedPreferences

    /** False when Android Keystore-backed storage is unavailable. Credentials are never written in plaintext. */
    val secureStorageAvailable: Boolean get() = encryptedPrefs != null

    init {
        val encrypted = runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
        legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        encryptedPrefs = encrypted
        if (encrypted != null) migrateLegacyCredentials(encrypted)
    }

    private val prefs: SharedPreferences? get() = encryptedPrefs

    /** Throws instead of silently downgrading to plaintext storage. */
    fun saveTelegram(token: String, channelId: String) {
        val secure = encryptedPrefs
            ?: throw IllegalStateException("Encrypted credential storage is unavailable on this device.")
        secure.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_CHANNEL, channelId.trim())
            .apply()
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun getChannelId(): String? = prefs?.getString(KEY_CHANNEL, null)?.takeIf { it.isNotBlank() }

    fun isConfigured(): Boolean = secureStorageAvailable && getToken() != null && getChannelId() != null

    fun maskedToken(): String {
        val token = getToken() ?: return "Not configured"
        val tail = token.takeLast(4)
        return "••••••••••••$tail"
    }

    fun disconnect() {
        encryptedPrefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_CHANNEL)?.apply()
        legacyPrefs.edit().remove(KEY_TOKEN).remove(KEY_CHANNEL).apply()
    }

    /** Moves credentials from old plaintext storage into Keystore-backed storage exactly once. */
    private fun migrateLegacyCredentials(secure: SharedPreferences) {
        val token = legacyPrefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return
        val channel = legacyPrefs.getString(KEY_CHANNEL, null)?.takeIf { it.isNotBlank() } ?: return
        secure.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CHANNEL, channel)
            .apply()
        legacyPrefs.edit().remove(KEY_TOKEN).remove(KEY_CHANNEL).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "promptvault_secure_prefs"
        const val LEGACY_PREFERENCES_NAME = "promptvault_secure_prefs_fallback"
        const val KEY_TOKEN = "telegram_bot_token"
        const val KEY_CHANNEL = "telegram_channel_id"
    }
}
