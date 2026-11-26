package com.promptvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.ItemAction
import com.promptvault.ui.components.ItemActionSheet
import com.promptvault.ui.components.ItemCard
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.GlassFab
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.viewmodels.ListViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun ListScreen(
    filter: ItemFilter,
    onNewContent: () -> Unit,
    onOpenItem: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ListViewModel = appViewModel { ListViewModel(it, filter) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sortExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PromptItem?>(null) }
    var moveTarget by remember { mutableStateOf<PromptItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }

    VaultScaffold(
        topBar = {
            AppTopBar(
                title = when (filter) {
                    ItemFilter.ALL -> "All Prompts"
                    ItemFilter.FAVORITES -> "Favorites"
                    ItemFilter.TEXT -> "Text Documents"
                    ItemFilter.CODE -> "Code Snippets"
                    else -> filter.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                onBack = onBack,
                actions = {
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort", modifier = Modifier.padding(end = 4.dp))
                            Text(state.sort.name.replace('_', ' '))
                        }
                        DropdownMenu(sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name.replace('_', ' ')) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            GlassFab(
                onClick = onNewContent,
                icon = Icons.Filled.Add,
                contentDescription = "New content"
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
                    shape = VaultShapes.compact
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${state.items.size} ${if (state.items.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Sorted by ${state.sort.name.lowercase().replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                        EmptyState(
                            title = "Nothing here yet",
                            message = "Create a new item or choose another category.",
                            icon = Icons.Filled.Inbox
                        )
                    }
                }
            }
            items(state.items, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    onClick = { onOpenItem(item.id) },
                    onLongClick = { selectedItem = item },
                    onFavorite = { viewModel.toggleFavorite(item.id) }
                )
            }
        }
    }

    selectedItem?.let { item ->
        ItemActionSheet(
            item = item,
            onDismiss = { selectedItem = null },
            onAction = { action ->
                when (action) {
                    ItemAction.OPEN, ItemAction.EDIT, ItemAction.RENAME -> onOpenItem(item.id)
                    ItemAction.COPY -> {
                        clipboard.setText(AnnotatedString(item.content))
                        showToast(context, "Copied to clipboard")
                    }
                    ItemAction.DUPLICATE -> viewModel.duplicate(item.id)
                    ItemAction.FAVORITE -> viewModel.toggleFavorite(item.id)
                    ItemAction.MOVE -> moveTarget = item
                    ItemAction.SHARE -> share(context, item.title, item.content)
                    ItemAction.DELETE -> deleteTarget = item
                }
                selectedItem = null
            }
        )
    }

    moveTarget?.let { item ->
        AlertDialogMove(
            item = item,
            folders = state.folders,
            onMove = { folderId ->
                viewModel.move(item.id, folderId)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }

    deleteTarget?.let { item ->
        ConfirmDialog(
            title = "Delete “${item.title}”?",
            message = "Local data will be removed. Cloud backups require explicit deletion.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(item.id, false)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun AlertDialogMove(
    item: PromptItem,
    folders: List<com.promptvault.domain.Folder>,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move “${item.title}”") },
        text = {
            Column {
                TextButton(onClick = { onMove(null) }) { Text("No folder (root)") }
                folders.forEach { folder ->
                    TextButton(onClick = { onMove(folder.id) }) { Text(folder.name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
