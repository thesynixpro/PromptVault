package com.promptvault.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.GlassDivider
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.theme.glassTextFieldColors
import com.promptvault.ui.viewmodels.TelegramViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun TelegramScreen(onBack: () -> Unit) {
    val viewModel: TelegramViewModel = appViewModel { TelegramViewModel(it) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showToken by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    val secure = state.secureStorageAvailable

    VaultScaffold(topBar = { AppTopBar("Telegram Cloud", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
        ) {
            GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.panel) {
                Row(
                    Modifier.padding(VaultSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (state.configured) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (state.configured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column(Modifier.padding(horizontal = VaultSpacing.md)) {
                        Text(
                            if (state.configured) "Connected" else "Not connected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (state.configured) {
                                "Saved channel: ${state.savedChannel}"
                            } else {
                                "Backups stay local until configured"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Create a bot with @BotFather, add it to a private channel as an administrator, then enter its token and channel ID.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { openBotFather(context) }) { Text("Open @BotFather") }

            if (!secure) {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    shape = VaultShapes.compact
                ) {
                    Text(
                        "Secure credential storage is unavailable on this device. Telegram cannot be configured because the bot token would otherwise be stored unencrypted.",
                        Modifier.padding(VaultSpacing.lg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Bot token") },
                modifier = Modifier.fillMaxWidth(),
                enabled = secure,
                singleLine = true,
                shape = VaultShapes.compact,
                colors = glassTextFieldColors(),
                visualTransformation = if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            "Toggle token visibility"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = state.channelId,
                onValueChange = viewModel::onChannelChange,
                label = { Text("Channel ID (e.g. -1001234567890 or @mychannel)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = secure,
                singleLine = true,
                shape = VaultShapes.compact,
                colors = glassTextFieldColors()
            )

            Text(
                "Saved token: ${state.maskedToken}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.isBusy && state.token.isNotBlank()
                ) { Text("Test connection") }
                OutlinedButton(
                    onClick = viewModel::verifyChannel,
                    enabled = !state.isBusy && state.token.isNotBlank()
                ) { Text("Verify channel") }
            }
            Button(
                onClick = { viewModel.saveConfiguration() },
                enabled = secure && !state.isBusy &&
                    state.token.isNotBlank() && state.channelId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.isBusy) "Working…" else "Save configuration") }

            if (state.status.isNotBlank()) {
                GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.compact) {
                    Text(
                        state.status,
                        Modifier.padding(VaultSpacing.lg),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            state.verification?.let { verification ->
                SectionTitle("Verification")
                GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.compact) {
                    Column(
                        Modifier.padding(VaultSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
                    ) {
                        Text("Bot: ${verification.botUsername}")
                        Text("Channel: ${verification.chatTitle} (${verification.chatId})")
                        Text("Administrator: ${if (verification.isAdministrator) "yes" else "no"}")
                        Text("Can post: ${if (verification.canPost) "yes" else "no"}")
                        Text("Can edit: ${if (verification.canEdit) "yes" else "no"}")
                        Text("Can delete: ${if (verification.canDelete) "yes" else "no"}")
                    }
                }
            }

            if (state.configured) {
                OutlinedButton(
                    onClick = { showDisconnect = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disconnect Telegram") }
            }

            SectionTitle("Security notes")
            GlassDivider()
            Text(
                "Credentials are encrypted with Android Keystore-backed preferences and are never logged. " +
                    "A bot token embedded in a distributed app can still be extracted from a device you do not control. " +
                    "For multi-user deployment, use a backend that holds the token and exposes scoped API endpoints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDisconnect) {
        ConfirmDialog(
            title = "Disconnect Telegram?",
            message = "Automatic backup will stop. Local items and existing backups are unchanged.",
            confirmLabel = "Disconnect",
            onConfirm = {
                viewModel.disconnect()
                showDisconnect = false
            },
            onDismiss = { showDisconnect = false }
        )
    }
}

private fun openBotFather(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather")))
    }
}
