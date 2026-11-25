package com.promptvault.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ExportRecord
import com.promptvault.domain.ItemFilter
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.SectionTitle
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
    var pendingExport by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var deleteTarget by remember { mutableStateOf<ExportRecord?>(null) }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            if (pendingExport != null) viewModel.exportItem(context, uri, pendingExport!!.first, false)
            else viewModel.exportAll(context, uri)
            pendingExport = null
        }
    }
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val target = pendingExport
        if (uri != null && target != null) viewModel.exportItem(context, uri, target.first, true)
        pendingExport = null
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

    Scaffold(
        topBar = { AppTopBar("Downloads & Backup", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { createJson.launch("promptvault-backup.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export all items as JSON") }
                    OutlinedButton(
                        onClick = { importFile.launch(arrayOf("text/plain", "application/json", "application/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import TXT or JSON backup") }
                }
                SectionTitle("Export individual items", action = {
                    Text("${listState.items.size} items")
                })
            }
            items(listState.items, key = { "item-${it.id}" }) { item ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(item.contentType.name, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            pendingExport = item.id to true
                            createText.launch("${item.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.txt")
                        }) { Text("TXT") }
                        TextButton(onClick = {
                            pendingExport = item.id to false
                            createJson.launch("${item.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
                        }) { Text("JSON") }
                    }
                }
            }
            item { SectionTitle("Export history") }
            if (state.exports.isEmpty()) {
                item { EmptyState("No exports yet", "Files you export will be listed here.", Icons.Filled.FileDownload) }
            }
            items(state.exports, key = { it.id }) { record ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(record.name, style = MaterialTheme.typography.titleMedium)
                            Text(com.promptvault.ui.components.formatDate(record.createdAt), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(android.net.Uri.parse(record.uri), record.mimeType)
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
