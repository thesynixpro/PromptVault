package com.promptvault.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.promptvault.BuildConfig
import com.promptvault.R
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.SectionTitle

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("About", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "PromptVault logo",
                modifier = Modifier.size(132.dp)
            )
            Text("PromptVault", style = MaterialTheme.typography.displaySmall)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "A local-first vault for prompts, notes, code snippets, and documents with optional Telegram cloud backup.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
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
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
            Row { Text("Material 3 • Kotlin • Jetpack Compose") }
        }
    }
}
