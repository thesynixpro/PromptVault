package com.promptvault.ui.screens
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.promptvault.domain.SyncTask
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.components.StatTile
import com.promptvault.ui.components.formatDate
import com.promptvault.ui.viewmodels.SyncViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun SyncScreen(onBack: () -> Unit) {
    val viewModel: SyncViewModel = appViewModel { SyncViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(SyncFilter.ALL) }
    var expandedTask by remember { mutableStateOf<String?>(null) }

    val tasks = when (filter) {
        SyncFilter.ALL -> state.tasks
        SyncFilter.PENDING -> state.tasks.filter { it.status == "PENDING" || it.status == "SYNCING" }
        SyncFilter.FAILED -> state.tasks.filter { it.status == "FAILED" }
        SyncFilter.HISTORY -> state.tasks.filter { it.status == "DONE" }
    }

    Scaffold(topBar = { AppTopBar("Sync Status", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Connected", if (state.connected) "Yes" else "No",
                        if (state.connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        Modifier.weight(1f))
                    StatTile("Pending", state.tasks.count { it.status == "PENDING" || it.status == "SYNCING" }.toString(),
                        Icons.Filled.Sync, Modifier.weight(1f))
                    StatTile("Failed", state.tasks.count { it.status == "FAILED" }.toString(),
                        Icons.Filled.CloudOff, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Last successful sync: ${formatDate(state.lastSuccessfulSync)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Automatic backup: ${if (state.autoBackup) "on" else "off"} • Wi-Fi only: ${if (state.wifiOnly) "yes" else "no"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::syncNow) { Text("Sync now") }
                    OutlinedButton(onClick = viewModel::retryFailed) { Text("Retry failed") }
                    OutlinedButton(onClick = viewModel::clearHistory) { Text("Clear history") }
                }
                SectionTitle("Tasks")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SyncFilter.entries.forEach { value ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = { Text(value.label) }
                        )
                    }
                }
            }
            if (tasks.isEmpty()) {
                item { EmptyState("No tasks", "Sync tasks appear here after you save or edit an item.") }
            }
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    expanded = expandedTask == task.id,
                    onExpand = { expandedTask = if (expandedTask == task.id) null else task.id }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(task: SyncTask, expanded: Boolean, onExpand: () -> Unit) {
    Card(
        onClick = onExpand,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.itemTitle.ifBlank { "Deleted item" }, style = MaterialTheme.typography.titleMedium)
            Text("${task.operation.replace('_', ' ')} • ${task.status} • retry ${task.retryCount}")
            Text(formatDate(task.lastAttempt ?: task.createdAt), style = MaterialTheme.typography.labelSmall)
            if (expanded) {
                Text(
                    task.errorMessage ?: "No error details. Telegram confirms successful operations before status changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.errorMessage != null) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
        }
    }
}

private enum class SyncFilter(val label: String) {
    ALL("All"), PENDING("Pending"), FAILED("Failed"), HISTORY("History")
}
