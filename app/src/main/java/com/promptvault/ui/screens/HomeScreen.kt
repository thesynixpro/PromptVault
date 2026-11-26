package com.promptvault.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import com.promptvault.domain.ViewMode
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.ItemAction
import com.promptvault.ui.components.ItemActionSheet
import com.promptvault.ui.components.ItemCard
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.components.StatTile
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.GlassFab
import com.promptvault.ui.theme.ScrollableFilterChips
import com.promptvault.ui.theme.VaultBrandMark
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
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
    val snackbarHostState = remember { SnackbarHostState() }

    var menuExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PromptItem?>(null) }
    var moveTarget by remember { mutableStateOf<PromptItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }
    var dragDistance by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    fun isAtTop(): Boolean {
        val firstIndex: Int
        val offset: Int
        if (settings.viewMode == ViewMode.GRID) {
            firstIndex = gridState.firstVisibleItemIndex
            offset = gridState.firstVisibleItemScrollOffset
        } else {
            firstIndex = listState.firstVisibleItemIndex
            offset = listState.firstVisibleItemScrollOffset
        }
        return firstIndex == 0 && offset == 0
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val pullModifier = Modifier.pointerInput(state.items.hashCode()) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dragDistance > 90f) viewModel.refresh()
                dragDistance = 0f
            }
        ) { _, amount ->
            if (amount > 0f && isAtTop()) dragDistance += amount else if (amount <= 0f) {
                dragDistance = 0f
            }
        }
    }

    VaultScaffold(
        topBar = {
            AppTopBar(
                title = "PromptVault",
                onMenu = onOpenDrawer,
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (settings.viewMode == ViewMode.LIST) "Grid view" else "List view"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (settings.viewMode == ViewMode.LIST) {
                                            Icons.Filled.GridView
                                        } else {
                                            Icons.Filled.ViewList
                                        },
                                        null
                                    )
                                },
                                onClick = {
                                    viewModel.setViewMode(
                                        if (settings.viewMode == ViewMode.LIST) {
                                            ViewMode.GRID
                                        } else {
                                            ViewMode.LIST
                                        }
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
            GlassFab(
                onClick = onNewContent,
                icon = Icons.Filled.Add,
                contentDescription = "New content"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val header: @Composable () -> Unit = {
            DashboardHeader(
                state = state,
                onFilter = { viewModel.setFilter(it) },
                onNavigate = onNavigate,
                onRefresh = { viewModel.refresh() }
            )
            SectionTitle("Recently saved", action = {
                TextButton(onClick = { onNavigate("list/${ItemFilter.ALL.name}") }) {
                    Text("See all")
                }
            })
            if (state.items.isEmpty()) {
                Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                    EmptyState(
                        title = "Your vault is ready",
                        message = "Create a prompt, text document, or code snippet to get started.",
                        icon = Icons.Filled.AutoAwesome
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (settings.viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pullModifier),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { header() }
                    gridItems(state.items, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            onClick = { onOpenItem(item.id) },
                            onLongClick = { selectedItem = item },
                            onFavorite = { viewModel.toggleFavorite(item.id) },
                            modifier = Modifier.padding(horizontal = VaultSpacing.sm)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pullModifier),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 88.dp
                    )
                ) {
                    item { header() }
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
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("Done") }
            },
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
    Column(
        Modifier.padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.md),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
    ) {
        GlassCard(shape = VaultShapes.panel) {
            Column(
                Modifier.padding(VaultSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultBrandMark(Modifier.size(44.dp))
                    Spacer(Modifier.size(VaultSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Your private vault", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.telegramConnected) {
                                "Telegram connected • ${state.stats.synced} synced"
                            } else {
                                "Offline-first • ${state.stats.total} items safe on this device"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (state.telegramConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (state.telegramConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                ) {
                    StatTile(
                        "Items",
                        state.stats.total.toString(),
                        Icons.Filled.AutoAwesome,
                        Modifier.weight(1f)
                    )
                    StatTile(
                        "Folders",
                        state.stats.folders.toString(),
                        Icons.Filled.Folder,
                        Modifier.weight(1f)
                    )
                    StatTile(
                        "Favorites",
                        state.stats.favorites.toString(),
                        Icons.Filled.Favorite,
                        Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            state.stats.failed > 0 -> "${state.stats.failed} failed uploads"
                            state.stats.pending > 0 -> "${state.stats.pending} waiting to upload"
                            state.telegramConnected -> "Everything is up to date"
                            else -> "Connect Telegram to back up your vault"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefresh) { Text("Sync now") }
                }
            }
        }

        ScrollableFilterChips(
            items = ItemFilter.entries.take(6),
            selected = state.filter,
            label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = onFilter
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
        ) {
            TextButton(onClick = { onNavigate("categories") }) { Text("Categories") }
            TextButton(onClick = { onNavigate("folders") }) { Text("Folders") }
            TextButton(onClick = { onNavigate("list/${ItemFilter.FAVORITES.name}") }) {
                Text("Favorites")
            }
            TextButton(onClick = { onNavigate("sync") }) { Text("Sync") }
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
