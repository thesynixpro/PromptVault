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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ContentType
import com.promptvault.domain.SortMode
import com.promptvault.domain.ThemeMode
import com.promptvault.domain.ViewMode
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.GlassDivider
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultSpacing
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
    var fontValue by remember(settings.fontSize) {
        mutableFloatStateOf(settings.fontSize.toFloat())
    }
    var spacingValue by remember(settings.lineSpacing) {
        mutableFloatStateOf(settings.lineSpacing.toFloat())
    }

    LaunchedEffect(Unit) { storage = viewModel.storageInfo(context) }

    VaultScaffold(topBar = { AppTopBar("Settings", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
        ) {
            SettingsSection("Appearance") {
                SettingRow(
                    "Theme",
                    settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { picker = SettingPicker.THEME }
                SettingSwitch("Dynamic colors", settings.dynamicColor, viewModel::setDynamicColor)
            }
            SettingsSection("Display") {
                SettingRow(
                    "View",
                    settings.viewMode.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { picker = SettingPicker.VIEW }
                SettingRow("Default sorting", settings.sortMode.name.replace('_', ' ')) {
                    picker = SettingPicker.SORT
                }
            }
            SettingsSection("Editor") {
                Text("Font size: ${fontValue.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fontValue,
                    onValueChange = { fontValue = it },
                    onValueChangeFinished = { viewModel.commitFontSize(fontValue.toInt()) },
                    valueRange = 12f..28f,
                    steps = 15
                )
                Text(
                    "Line spacing: ${spacingValue.toInt()}sp",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = spacingValue,
                    onValueChange = { spacingValue = it },
                    onValueChangeFinished = { viewModel.commitLineSpacing(spacingValue.toInt()) },
                    valueRange = 0f..16f,
                    steps = 7
                )
                SettingSwitch("Automatic draft saving", settings.autoSave, viewModel::setAutoSave)
                SettingRow(
                    "Default content type",
                    settings.defaultContentType.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { picker = SettingPicker.TYPE }
            }
            SettingsSection("Synchronization") {
                SettingSwitch("Automatic backup", settings.autoBackup, viewModel::setAutoBackup)
                SettingSwitch("Wi-Fi only", settings.wifiOnly, viewModel::setWifiOnly)
                TextButton(onClick = { onNavigate("sync") }) { Text("Open sync dashboard") }
            }
            SettingsSection("Storage") {
                Text(
                    "${storage.itemCount} items • ${formatBytes(storage.databaseBytes)} database",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { onNavigate("downloads") }) { Text("Export all data") }
                TextButton(onClick = {
                    viewModel.clearCache(context)
                    showToast(context, "Cache cleared")
                }) { Text("Clear cache") }
            }
            SettingsSection("Security") {
                SettingSwitch(
                    "Hide sensitive information",
                    settings.hideSensitive,
                    viewModel::setHideSensitive
                )
                Text(
                    "Telegram token: ${telegramState.maskedToken}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!telegramState.secureStorageAvailable) {
                    Text(
                        "Secure credential storage is unavailable on this device. Telegram setup is disabled to avoid storing a bot token in plaintext.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (telegramState.configured) {
                    TextButton(onClick = { showDisconnect = true }) {
                        Text("Disconnect Telegram")
                    }
                }
                Text(
                    "Bot credentials are protected by Android Keystore-backed encryption. Do not share a device that has a configured bot token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    picker?.let { which ->
        val options: List<Pair<String, () -> Unit>> = when (which) {
            SettingPicker.THEME -> ThemeMode.entries.map { mode ->
                humanLabel(mode.name) to ({ viewModel.setTheme(mode) })
            }
            SettingPicker.VIEW -> ViewMode.entries.map { mode ->
                humanLabel(mode.name) to ({ viewModel.setViewMode(mode) })
            }
            SettingPicker.SORT -> SortMode.entries.map { mode ->
                humanLabel(mode.name.replace('_', ' ')) to ({ viewModel.setSort(mode) })
            }
            SettingPicker.TYPE -> ContentType.entries.map { type ->
                humanLabel(type.name) to ({ viewModel.setDefaultType(type) })
            }
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
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(VaultSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            GlassDivider()
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
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

private enum class SettingPicker { THEME, VIEW, SORT, TYPE }

private fun humanLabel(value: String): String =
    value.lowercase().replaceFirstChar { it.uppercase() }

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
