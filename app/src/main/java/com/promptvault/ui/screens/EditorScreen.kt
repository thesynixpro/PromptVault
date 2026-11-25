package com.promptvault.ui.screens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptvault.domain.ContentType
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.viewmodels.EditorViewModel
import com.promptvault.ui.viewmodels.appViewModel
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    itemId: String,
    initialType: ContentType,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val viewModel: EditorViewModel = appViewModel { EditorViewModel(it, itemId, initialType) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var categoryMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun attemptBack() {
        if (state.dirty) showExitDialog = true else onBack()
    }

    BackHandler(enabled = true) { attemptBack() }

    LaunchedEffect(state.savedItemId) {
        state.savedItemId?.let { id ->
            snackbarHostState.showSnackbar("Saved to your local vault")
            if (state.isNew) onSaved(id)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = if (state.isNew) "New ${state.contentType.name.lowercase()}" else "Edit item",
                onBack = { attemptBack() },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.Filled.Undo, "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.Filled.Redo, "Redo")
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "Favorite"
                        )
                    }
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Filled.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenu = true }) {
                        Text(state.contentType.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(typeMenu, onDismissRequest = { typeMenu = false }) {
                        ContentType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    viewModel.onTypeChange(type)
                                    typeMenu = false
                                }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { categoryMenu = true }) {
                        Text(state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "No category")
                    }
                    DropdownMenu(categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("No category") }, onClick = {
                            viewModel.onCategoryChange(null)
                            categoryMenu = false
                        })
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.onCategoryChange(category.id)
                                    categoryMenu = false
                                }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { folderMenu = true }) {
                        Text(state.folders.firstOrNull { it.id == state.folderId }?.name ?: "No folder")
                    }
                    DropdownMenu(folderMenu, onDismissRequest = { folderMenu = false }) {
                        DropdownMenuItem(text = { Text("No folder") }, onClick = {
                            viewModel.onFolderChange(null)
                            folderMenu = false
                        })
                        state.folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = {
                                    viewModel.onFolderChange(folder.id)
                                    folderMenu = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.tagsRaw,
                onValueChange = viewModel::onTagsChange,
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::onContentChange,
                label = { Text("Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = if (state.contentType == ContentType.CODE) FontFamily.Monospace else FontFamily.Default,
                    fontSize = state.fontSize.sp,
                    lineHeight = (state.fontSize + state.lineSpacing).sp
                ),
                keyboardOptions = KeyboardOptions.Default,
                maxLines = 2000
            )

            Text(
                "${state.wordCount} words • ${state.characterCount} characters • ${state.lineCount} lines" +
                    if (state.draftSaved && state.autoSave) " • Draft saved" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(state.content))
                    showToast(context, "Content copied")
                }) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = { share(context, state.title, state.content) }) {
                    Icon(Icons.Filled.Share, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Share")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { viewModel.save() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Save")
                }
                if (!state.isNew) {
                    OutlinedButton(onClick = { viewModel.duplicate() }) { Text("Duplicate") }
                    OutlinedButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Delete")
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = "Leave without saving?",
            message = if (state.autoSave) {
                "A draft has been saved automatically and will be available when you return."
            } else {
                "Your unsaved changes will be lost."
            },
            confirmLabel = "Leave",
            onConfirm = {
                showExitDialog = false
                onBack()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this item?",
            message = "Local data will be removed. Telegram backups are kept unless requested from the sync dashboard.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
                scope.launch { snackbarHostState.showSnackbar("Item deleted") }
                onDeleted()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
