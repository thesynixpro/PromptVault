package com.promptvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Default.Add
import androidx.compose.material.icons.Default.Delete
import androidx.compose.material.icons.Default.Edit
import androidx.compose.material.icons.Default.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.promptvault.domain.Folder
import com.promptvault.domain.PromptItem
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.ItemCard
import com.promptvault.ui.viewmodels.FoldersViewModel
import com.promptvault.ui.viewmodels.appViewModel

private val folderColors = listOf(0xFF1565C0L, 0xFF00838FL, 0xFF6A1B9AL, 0xFF2E7D32L, 0xFFEF6C00L)

@Composable
fun FoldersScreen(
    parentId: String?,
    autoCreate: Boolean,
    onNavigateFolder: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    val viewModel: FoldersViewModel = appViewModel { FoldersViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Folder?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(folderColors.first()) }
    var deleting by remember { mutableStateOf<Folder?>(null) }
    var deleteContents by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<PromptItem?>(null) }

    LaunchedEffect(parentId) { viewModel.openFolder(parentId) }
    LaunchedEffect(autoCreate) {
        if (autoCreate) {
            editing = null
            name = ""
            color = folderColors.first()
            showEditor = true
        }
    }

    fun openEditor(folder: Folder?) {
        editing = folder
        name = folder?.name.orEmpty()
        color = folder?.color ?: folderColors.first()
        showEditor = true
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.currentFolder?.name ?: "Folders",
                onBack = {
                    if (parentId != null) onNavigateFolder(state.currentFolder?.parentFolderId)
                    else onBack()
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openEditor(null) }) {
                Icon(Icons.Default.Add, "New folder")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                if (parentId != null) {
                    TextButton(onClick = { onNavigateFolder(null) }) { Text("All folders /") }
                }
            }
            if (state.childFolders.isEmpty() && state.items.isEmpty()) {
                item {
                    EmptyState(
                        "This folder is empty",
                        "Create a nested folder or move items here.",
                        Icons.Default.Folder
                    )
                }
            }
            items(state.childFolders, key = { it.id }) { folder ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color(folder.color).copy(alpha = 0.12f)
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = Color(folder.color))
                        Text(
                            folder.name,
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { openEditor(folder) }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = {
                            deleting = folder
                            deleteContents = false
                        }) { Icon(Icons.Default.Delete, "Delete") }
                        TextButton(onClick = { onNavigateFolder(folder.id) }) { Text("Open") }
                    }
                }
            }
            item { androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            items(state.items, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    onClick = { onOpenItem(item.id) },
                    onLongClick = { moveTarget = item },
                    onFavorite = { viewModel.toggleFavorite(item.id) }
                )
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editing == null) "New folder" else "Rename folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Folder name") },
                        singleLine = true
                    )
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        folderColors.forEach { value ->
                            androidx.compose.material3.FilterChip(
                                selected = color == value,
                                onClick = { color = value },
                                label = { Text(" ") },
                                leadingIcon = {
                                    Box(Modifier.size(16.dp).background(Color(value), CircleShape))
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        if (editing == null) viewModel.create(name, color, "folder")
                        else viewModel.update(editing!!.id, name, color, editing!!.icon)
                        showEditor = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }

    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${folder.name}”?") },
            text = {
                Column {
                    Text("What should happen to its contents?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !deleteContents, onClick = { deleteContents = false })
                        Text("Move contents to parent folder")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = deleteContents, onClick = { deleteContents = true })
                        Text("Delete all contents too")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(folder.id, deleteContents)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    moveTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move “${item.title}”") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.moveItem(item.id, null)
                        moveTarget = null
                    }) { Text("Root") }
                    state.folders.filter { it.id != parentId }.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.moveItem(item.id, folder.id)
                            moveTarget = null
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }
}
