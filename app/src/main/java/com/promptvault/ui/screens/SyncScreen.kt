package com.promptvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.promptvault.domain.SyncTask
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.components.StatTile
import com.promptvault.ui.components.formatDate
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.ScrollableFilterChips
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.viewmodels.SyncViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun SyncScreen(onBack: () -> Unit) {
    val viewModel: SyncViewModel = appViewModel { SyncViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(SyncFilter.ALL) }
    var expandedTask by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val tasks = when (filter) {
        SyncFilter.ALL -> state.tasks
        SyncFilter.PENDING -> state.tasks.filter { it.status == "PENDING" || it.status == "SYNCING" }
        SyncFilter.FAILED -> state.tasks.filter { it.status == "FAILED" }
        SyncFilter.HISTORY -> state.tasks.filter { it.status == "DONE" }
    }

    VaultScaffold(
        topBar = { AppTopBar("Sync Status", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isSyncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        Modifier.padding(horizontal = VaultSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                            StatTile(
                                "Connected",
                                if (state.connected) "Yes" else "No",
                                if (state.connected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                Modifier.weight(1f)
                            )
                            StatTile(
                                "Pending",
                                state.tasks.count {
                                    it.status == "PENDING" || it.status == "SYNCING"
                                }.toString(),
                                Icons.Filled.Sync,
                                Modifier.weight(1f)
                            )
                            StatTile(
                                "Failed",
                                state.tasks.count { it.status == "FAILED" }.toString(),
                                Icons.Filled.CloudOff,
                                Modifier.weight(1f)
                            )
                        }
                        GlassCard(Modifier.fillMaxWidth(), shape = VaultShapes.compact) {
                            Column(
                                Modifier.padding(VaultSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
                            ) {
                                Text(
                                    "Last successful sync: ${formatDate(state.lastSuccessfulSync)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Automatic backup: ${if (state.autoBackup) "on" else "off"} • " +
                                        "Wi-Fi only: ${if (state.wifiOnly) "yes" else "no"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                            OutlinedButton(
                                onClick = viewModel::syncNow,
                                enabled = !state.isSyncing
                            ) { Text("Sync now") }
                            OutlinedButton(onClick = viewModel::retryFailed) { Text("Retry failed") }
                            OutlinedButton(onClick = viewModel::clearHistory) { Text("Clear") }
                        }
                        SectionTitle("Tasks")
                        ScrollableFilterChips(
                            items = SyncFilter.entries,
                            selected = filter,
                            label = { it.label },
                            onSelect = { filter = it }
                        )
                    }
                }
                if (tasks.isEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                            EmptyState(
                                "No tasks",
                                "Sync tasks appear here after you save or edit an item."
                            )
                        }
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        expanded = expandedTask == task.id,
                        onExpand = {
                            expandedTask = if (expandedTask == task.id) null else task.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: SyncTask, expanded: Boolean, onExpand: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xs)
            .clickable(onClick = onExpand)
            .semantics { role = Role.Button },
        shape = VaultShapes.compact,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.padding(VaultSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    task.itemTitle.ifBlank { "Deleted item" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    task.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (task.status) {
                        "FAILED" -> MaterialTheme.colorScheme.error
                        "DONE" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                "${task.operation.replace('_', ' ')} • retry ${task.retryCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatDate(task.lastAttempt ?: task.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                Spacer(Modifier.height(VaultSpacing.xs))
                Text(
                    task.errorMessage
                        ?: "No error details. Telegram confirms successful operations before status changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                )
            }
        }
    }
}

private enum class SyncFilter(val label: String) {
    ALL("All"), PENDING("Pending"), FAILED("Failed"), HISTORY("History")
}
