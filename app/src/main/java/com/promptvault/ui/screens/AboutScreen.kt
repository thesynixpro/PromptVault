package com.promptvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.promptvault.BuildConfig
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.VaultBrandMark
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing

@Composable
fun AboutScreen(onBack: () -> Unit) {
    VaultScaffold(topBar = { AppTopBar("About", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
        ) {
            VaultBrandMark(Modifier.size(120.dp))
            Text("PromptVault", style = MaterialTheme.typography.displaySmall)
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "A local-first vault for prompts, notes, code snippets, and documents with optional Telegram cloud backup.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.panel) {
                Column(
                    Modifier.padding(VaultSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
                ) {
                    Text("Built for", style = MaterialTheme.typography.titleMedium)
                    Text("• AI prompts and reusable templates")
                    Text("• Long text documents and personal notes")
                    Text("• Code snippets with preserved formatting")
                    Text("• Offline-first Room database and instant search")
                    Text("• Background Telegram backup with retry queue")
                }
            }
            SectionTitle("Privacy")
            Text(
                "Your content stays on your device unless you explicitly configure Telegram backup. " +
                    "Credentials are encrypted with Android Keystore-backed preferences. " +
                    "A Telegram bot token embedded in an app can be extracted by anyone with physical access to that device; " +
                    "for multi-user deployments, use a backend that keeps the token server-side.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(VaultSpacing.lg))
            Row { Text("Material 3 • Kotlin • Jetpack Compose") }
        }
    }
}
