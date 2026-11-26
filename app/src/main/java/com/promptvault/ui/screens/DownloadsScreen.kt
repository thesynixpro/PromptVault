package com.promptvault.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ExportRecord
import com.promptvault.domain.ItemFilter
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.components.formatDate
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.viewmodels.DownloadsViewModel
import com.promptvault.ui.viewmodels.ListViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val viewModel: DownloadsViewModel = appViewModel { DownloadsViewModel(it) }
    val listViewModel: ListViewModel = appViewModel { ListViewModel(it, ItemFilter.ALL) }
    val state by viewModel.uiState.collectAsState()
    val listState by listViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingTextExport by remember { mutableStateOf<String?>(null) }
    var pendingJsonRequest by remember { mutableStateOf<JsonExportRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<ExportRecord?>(null) }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val request = pendingJsonRequest
        if (uri != null && request != null) {
            when (request) {
                is JsonExportRequest.All -> viewModel.exportAll(context, uri)
                is JsonExportRequest.Item -> viewModel.exportItem(context, uri, request.id, false)
            }
        }
        pendingJsonRequest = null
    }
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val target = pendingTextExport
        if (uri != null && target != null) viewModel.exportItem(context, uri, target, true)
        pendingTextExport = null
    }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.import(context, uri) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbar.showSnackbar(state.message)
            viewModel.clearMessage()
        }
    }

    VaultScaffold(
        topBar = { AppTopBar("Downloads & Backup", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                ) {
                    GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.panel) {
                        Column(
                            Modifier.padding(VaultSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                        ) {
                            Text(
                                "Your data, your files",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Export a full JSON backup or individual TXT/JSON files using the system file picker. No network is required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    pendingJsonRequest = JsonExportRequest.All
                                    createJson.launch("promptvault-backup.json")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Export all items as JSON") }
                            OutlinedButton(
                                onClick = {
                                    importFile.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/json",
                                            "application/*",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Import TXT or JSON backup") }
                        }
                    }
                    SectionTitle("Export individual items", action = {
                        Text("${listState.items.size} items")
                    })
                }
            }
            items(listState.items, key = { "item-${it.id}" }) { item ->
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xs),
                    shape = VaultShapes.compact,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        Modifier.padding(VaultSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = VaultSpacing.md)
                        ) {
                            Text(
                                item.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.contentType.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            pendingTextExport = item.id
                            createText.launch(
                                "${item.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.txt"
                            )
                        }) { Text("TXT") }
                        TextButton(onClick = {
                            pendingJsonRequest = JsonExportRequest.Item(item.id)
                            createJson.launch(
                                "${item.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json"
                            )
                        }) { Text("JSON") }
                    }
                }
            }
            item {
                Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                    SectionTitle("Export history")
                }
            }
            if (state.exports.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                        EmptyState(
                            "No exports yet",
                            "Files you export will be listed here.",
                            Icons.Filled.FileDownload
                        )
                    }
                }
            }
            items(state.exports, key = { it.id }) { record ->
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xs),
                    shape = VaultShapes.compact,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        Modifier.padding(VaultSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatDate(record.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        android.net.Uri.parse(record.uri),
                                        record.mimeType
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        }) { Icon(Icons.Filled.OpenInNew, "Open file") }
                        IconButton(onClick = { deleteTarget = record }) {
                            Icon(Icons.Filled.Delete, "Remove from history")
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { record ->
        ConfirmDialog(
            title = "Remove from history?",
            message = "The exported file itself is not deleted.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.delete(record)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

private sealed interface JsonExportRequest {
    data object All : JsonExportRequest
    data class Item(val id: String) : JsonExportRequest
}
