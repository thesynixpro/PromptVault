package com.promptvault.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.promptvault.domain.ContentType
import com.promptvault.domain.SortMode
import com.promptvault.domain.ThemeMode
import com.promptvault.domain.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("promptvault_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortMode: SortMode = SortMode.RECENTLY_MODIFIED,
    val fontSize: Int = 16,
    val lineSpacing: Int = 4,
    val autoSave: Boolean = true,
    val defaultContentType: ContentType = ContentType.PROMPT,
    val autoBackup: Boolean = true,
    val wifiOnly: Boolean = false,
    val hideSensitive: Boolean = true,
    val lastSuccessfulSync: Long = 0L
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val VIEW = stringPreferencesKey("view_mode")
        val SORT = stringPreferencesKey("sort_mode")
        val FONT_SIZE = intPreferencesKey("font_size")
        val LINE_SPACING = intPreferencesKey("line_spacing")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val DEFAULT_TYPE = stringPreferencesKey("default_content_type")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val HIDE_SENSITIVE = booleanPreferencesKey("hide_sensitive")
        val LAST_SYNC = stringPreferencesKey("last_successful_sync")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC] ?: true,
            viewMode = runCatching { ViewMode.valueOf(prefs[Keys.VIEW] ?: "") }
                .getOrDefault(ViewMode.LIST),
            sortMode = runCatching { SortMode.valueOf(prefs[Keys.SORT] ?: "") }
                .getOrDefault(SortMode.RECENTLY_MODIFIED),
            fontSize = prefs[Keys.FONT_SIZE] ?: 16,
            lineSpacing = prefs[Keys.LINE_SPACING] ?: 4,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true,
            defaultContentType = runCatching { ContentType.valueOf(prefs[Keys.DEFAULT_TYPE] ?: "") }
                .getOrDefault(ContentType.PROMPT),
            autoBackup = prefs[Keys.AUTO_BACKUP] ?: true,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            hideSensitive = prefs[Keys.HIDE_SENSITIVE] ?: true,
            lastSuccessfulSync = (prefs[Keys.LAST_SYNC] ?: "0").toLongOrNull() ?: 0L
        )
    }

    private suspend fun edit(block: androidx.datastore.preferences.core.MutablePreferences.() -> Unit) {
        context.settingsDataStore.edit(block)
    }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC] = value }
    suspend fun setViewMode(value: ViewMode) = edit { it[Keys.VIEW] = value.name }
    suspend fun setSortMode(value: SortMode) = edit { it[Keys.SORT] = value.name }
    suspend fun setFontSize(value: Int) = edit { it[Keys.FONT_SIZE] = value.coerceIn(12, 28) }
    suspend fun setLineSpacing(value: Int) = edit { it[Keys.LINE_SPACING] = value.coerceIn(0, 16) }
    suspend fun setAutoSave(value: Boolean) = edit { it[Keys.AUTO_SAVE] = value }
    suspend fun setDefaultContentType(value: ContentType) = edit { it[Keys.DEFAULT_TYPE] = value.name }
    suspend fun setAutoBackup(value: Boolean) = edit { it[Keys.AUTO_BACKUP] = value }
    suspend fun setWifiOnly(value: Boolean) = edit { it[Keys.WIFI_ONLY] = value }
    suspend fun setHideSensitive(value: Boolean) = edit { it[Keys.HIDE_SENSITIVE] = value }
    suspend fun setLastSuccessfulSync(value: Long) = edit { it[Keys.LAST_SYNC] = value.toString() }
}
