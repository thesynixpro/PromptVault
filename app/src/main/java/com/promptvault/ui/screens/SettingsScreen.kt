package com.promptvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.promptvault.data.preferences.AppSettings
import com.promptvault.domain.ContentType
import com.promptvault.domain.SortMode
import com.promptvault.domain.ThemeMode
import com.promptvault.domain.ViewMode
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.viewmodels.SettingsViewModel
import com.promptvault.ui.viewmodels.StorageInfo
import com.promptvault.ui.viewmodels.TelegramViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val telegramViewModel: TelegramViewModel = appViewModel { TelegramViewModel(it) }
    val settings by viewModel.settings.collectAsState()
    val telegramState by telegramViewModel.state.collectAsState()
    val context = LocalContext.current
    var storage by remember { mutableStateOf(StorageInfo()) }
    var picker by remember { mutableStateOf<SettingPicker?>(null) }
    var showDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { storage = viewModel.storageInfo(context) }

    Scaffold(topBar = { AppTopBar("Settings", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsSection("Appearance") {
                SettingRow("Theme", settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }) {
                    picker = SettingPicker.THEME
                }
                SettingSwitch("Dynamic colors", settings.dynamicColor, viewModel::setDynamicColor)
            }
            SettingsSection("Display") {
                SettingRow("View", settings.viewMode.name.lowercase().replaceFirstChar { it.uppercase() }) {
                    picker = SettingPicker.VIEW
                }
                SettingRow("Default sorting", settings.sortMode.name.replace('_', ' ')) {
                    picker = SettingPicker.SORT
                }
            }
            SettingsSection("Editor") {
                Text("Font size: ${settings.fontSize}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 12f..28f,
                    steps = 15
                )
                Text("Line spacing: ${settings.lineSpacing}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.lineSpacing.toFloat(),
                    onValueChange = { viewModel.setLineSpacing(it.toInt()) },
                    valueRange = 0f..16f,
                    steps = 7
                )
                SettingSwitch("Automatic draft saving", settings.autoSave, viewModel::setAutoSave)
                SettingRow("Default content type", settings.defaultContentType.name.lowercase().replaceFirstChar { it.uppercase() }) {
                    picker = SettingPicker.TYPE
                }
            }
            SettingsSection("Synchronization") {
                SettingSwitch("Automatic backup", settings.autoBackup, viewModel::setAutoBackup)
                SettingSwitch("Wi-Fi only", settings.wifiOnly, viewModel::setWifiOnly)
                TextButton(onClick = { onNavigate("sync") }) { Text("Open sync dashboard") }
            }
            SettingsSection("Storage") {
                Text("${storage.itemCount} items • ${formatBytes(storage.databaseBytes)} database")
                TextButton(onClick = { onNavigate("downloads") }) { Text("Export all data") }
                TextButton(onClick = { viewModel.clearCache(context); showToast(context, "Cache cleared") }) {
                    Text("Clear cache")
                }
            }
            SettingsSection("Security") {
                SettingSwitch("Hide sensitive information", settings.hideSensitive, viewModel::setHideSensitive)
                Text("Telegram token: ${telegramState.maskedToken}", style = MaterialTheme.typography.bodyMedium)
                if (telegramState.configured) {
                    TextButton(onClick = { showDisconnect = true }) { Text("Disconnect Telegram") }
                }
                Text(
                    "Bot credentials are protected by Android Keystore-backed encryption. Do not share a device that has a configured bot token.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    picker?.let { which ->
        val options: List<Pair<String, () -> Unit>> = when (which) {
            SettingPicker.THEME -> ThemeMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to { viewModel.setTheme(it) } }
            SettingPicker.VIEW -> ViewMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to { viewModel.setViewMode(it) } }
            SettingPicker.SORT -> SortMode.entries.map { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } to { viewModel.setSort(it) } }
            SettingPicker.TYPE -> ContentType.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to { viewModel.setDefaultType(it) } }
        }
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Choose an option") },
            text = {
                Column {
                    options.forEach { (label, action) ->
                        TextButton(onClick = {
                            action()
                            picker = null
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } }
        )
    }

    if (showDisconnect) {
        ConfirmDialog(
            title = "Disconnect Telegram?",
            message = "Local content is never affected.",
            confirmLabel = "Disconnect",
            onConfirm = {
                viewModel.disconnectTelegram()
                showDisconnect = false
            },
            onDismiss = { showDisconnect = false }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.padding(vertical = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private enum class SettingPicker { THEME, VIEW, SORT, TYPE }

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
