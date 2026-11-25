package com.promptvault.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Default.Add
import androidx.compose.material.icons.Default.AutoAwesome
import androidx.compose.material.icons.Default.CloudDone
import androidx.compose.material.icons.Default.CloudOff
import androidx.compose.material.icons.Default.Favorite
import androidx.compose.material.icons.Default.Folder
import androidx.compose.material.icons.Default.GridView
import androidx.compose.material.icons.Default.Menu
import androidx.compose.material.icons.Default.MoreVert
import androidx.compose.material.icons.Default.Search
import androidx.compose.material.icons.Default.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.promptvault.R
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import com.promptvault.domain.ViewMode
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.ItemAction
import com.promptvault.ui.components.ItemActionSheet
import com.promptvault.ui.components.ItemCard
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.components.StatTile
import com.promptvault.ui.viewmodels.HomeViewModel
import com.promptvault.ui.viewmodels.SettingsViewModel
import com.promptvault.ui.viewmodels.appViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onNewContent: () -> Unit,
    onOpenItem: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: HomeViewModel = appViewModel { HomeViewModel(it) }
    val settingsViewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var menuExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PromptItem?>(null) }
    var moveTarget by remember { mutableStateOf<PromptItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }
    var dragDistance by remember { mutableStateOf(0f) }

    val pullModifier = Modifier.pointerInput(state.items.hashCode()) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dragDistance > 90f) viewModel.refresh()
                dragDistance = 0f
            }
        ) { _, amount ->
            if (amount > 0f) dragDistance += amount else dragDistance = 0f
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = "PromptVault logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("PromptVault")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Open navigation")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "Search") }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (settings.viewMode == ViewMode.LIST) "Grid view" else "List view") },
                                leadingIcon = {
                                    Icon(
                                        if (settings.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                        null
                                    )
                                },
                                onClick = {
                                    viewModel.setViewMode(
                                        if (settings.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                                    )
                                    menuExpanded = false
                                }
                            )
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name.replace('_', ' ')) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewContent) {
                Icon(Icons.Default.Add, "New content")
            }
        }
    ) { padding ->
        val content: @Composable () -> Unit = {
            DashboardHeader(
                state = state,
                onFilter = { viewModel.setFilter(it) },
                onNavigate = onNavigate,
                onRefresh = { viewModel.refresh() }
            )
            SectionTitle("Recently saved", action = {
                TextButton(onClick = { onNavigate("list/${ItemFilter.ALL.name}") }) { Text("See all") }
            })
            if (state.items.isEmpty()) {
                EmptyState(
                    title = "Your vault is ready",
                    message = "Create a prompt, text document, or code snippet to get started.",
                    icon = Icons.Default.AutoAwesome
                )
            }
        }

        if (settings.viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(pullModifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { content() }
                gridItems(state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        onLongClick = { selectedItem = item },
                        onFavorite = { viewModel.toggleFavorite(item.id) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(72.dp)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(pullModifier)
            ) {
                item { content() }
                items(state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        onLongClick = { selectedItem = item },
                        onFavorite = { viewModel.toggleFavorite(item.id) }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
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
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move “${item.title}”") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.move(item.id, null)
                        moveTarget = null
                    }) { Text("No folder (root)") }
                    state.folders.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.move(item.id, folder.id)
                            moveTarget = null
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete “${item.title}”?") },
            text = { Text("Local deletion never removes a Telegram backup unless you explicitly request it.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id, false)
                    deleteTarget = null
                }) { Text("Delete locally") }
            },
            dismissButton = {
                Row {
                    if (item.telegramMessageId != null) {
                        TextButton(onClick = {
                            viewModel.delete(item.id, true)
                            deleteTarget = null
                        }) { Text("Also cloud") }
                    }
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    state: com.promptvault.ui.viewmodels.HomeUiState,
    onFilter: (ItemFilter) -> Unit,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Saved items", state.stats.total.toString(), Icons.Default.AutoAwesome, Modifier.weight(1f))
            StatTile("Folders", state.stats.folders.toString(), Icons.Default.Folder, Modifier.weight(1f))
            StatTile("Favorites", state.stats.favorites.toString(), Icons.Default.Favorite, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.telegramConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                null,
                tint = if (state.telegramConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (state.telegramConnected) {
                    "Telegram connected • ${state.stats.synced} synced • ${state.stats.pending} pending"
                } else {
                    "Offline-first • Telegram not connected"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Sync") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemFilter.entries.take(6).forEach { filter ->
                androidx.compose.material3.FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onNavigate("categories") }) { Text("Categories") }
            TextButton(onClick = { onNavigate("folders") }) { Text("Folders") }
            TextButton(onClick = { onNavigate("favorites") }) { Text("Favorites") }
            TextButton(onClick = { onNavigate("sync") }) { Text("Sync status") }
        }
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun share(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share PromptVault item")) }
}
