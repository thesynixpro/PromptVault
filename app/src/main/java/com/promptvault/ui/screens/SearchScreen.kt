package com.promptvault.ui.screens
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.ItemAction
import com.promptvault.ui.components.ItemActionSheet
import com.promptvault.ui.components.ItemCard
import com.promptvault.ui.components.SectionTitle
import com.promptvault.ui.viewmodels.SearchViewModel
import com.promptvault.ui.viewmodels.appViewModel

@Composable
fun SearchScreen(
    onOpenDrawer: () -> Unit,
    onOpenItem: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: SearchViewModel = appViewModel { SearchViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sortExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PromptItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Search",
                onBack = onBack,
                actions = {
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
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
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("Search titles, content, tags, categories…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Close, "Clear query")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.commitSearch() })
                )
                Row {
                    ItemFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                if (state.query.isBlank() && state.recent.isNotEmpty()) {
                    SectionTitle("Recent searches", action = {
                        TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                    })
                    state.recent.take(8).forEach { term ->
                        TextButton(onClick = { viewModel.onQueryChange(term) }) { Text(term) }
                    }
                }
                if (state.query.isNotBlank() && state.suggestions.isNotEmpty()) {
                    SectionTitle("Suggestions")
                    state.suggestions.forEach { term ->
                        TextButton(onClick = { viewModel.onQueryChange(term) }) { Text(term) }
                    }
                }
                SectionTitle("${state.results.size} results")
            }
            if (state.results.isEmpty() && state.query.isNotBlank()) {
                item {
                    EmptyState(
                        title = "No matches",
                        message = "Try a different word, tag, or category.",
                        icon = Icons.Filled.Search
                    )
                }
            }
            items(state.results, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    onClick = {
                        viewModel.commitSearch()
                        onOpenItem(item.id)
                    },
                    onLongClick = { selectedItem = item },
                    onFavorite = { viewModel.toggleFavorite(item.id) },
                    modifier = Modifier.padding(horizontal = 0.dp)
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
                    ItemAction.DUPLICATE -> viewModel.duplicateItem(item.id)
                    ItemAction.FAVORITE -> viewModel.toggleFavorite(item.id)
                    ItemAction.SHARE -> share(context, item.title, item.content)
                    ItemAction.MOVE -> onNavigate("folders")
                    ItemAction.DELETE -> deleteTarget = item
                }
                selectedItem = null
            }
        )
    }

    deleteTarget?.let { item ->
        com.promptvault.ui.components.ConfirmDialog(
            title = "Delete “${item.title}”?",
            message = "This removes the local copy. Telegram backups are kept unless requested explicitly.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteItem(item.id, false)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
