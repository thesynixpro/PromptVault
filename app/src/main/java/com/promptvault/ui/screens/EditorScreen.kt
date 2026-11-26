package com.promptvault.ui.screens

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
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
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.theme.glassTextFieldColors
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

    BackHandler(enabled = !showExitDialog && !showDeleteDialog) { attemptBack() }

    LaunchedEffect(state.savedItemId) {
        state.savedItemId?.let { id ->
            val wasNew = state.isNew
            viewModel.consumeSaved()
            snackbarHostState.showSnackbar("Saved to your local vault")
            if (wasNew) onSaved(id)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    VaultScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = if (state.isNew) "New ${state.contentType.name.lowercase()}" else "Edit item",
                onBack = { attemptBack() },
                actions = {
                    IconButton(
                        onClick = viewModel::undo,
                        enabled = state.canUndo && !state.isSaving
                    ) {
                        Icon(Icons.Filled.Undo, "Undo")
                    }
                    IconButton(
                        onClick = viewModel::redo,
                        enabled = state.canRedo && !state.isSaving
                    ) {
                        Icon(Icons.Filled.Redo, "Redo")
                    }
                    IconButton(onClick = viewModel::toggleFavorite, enabled = !state.isSaving) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "Favorite"
                        )
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = !state.isSaving) {
                        Icon(Icons.Filled.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.loaded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@VaultScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
        ) {
            GlassCard(shape = VaultShapes.panel) {
                Column(
                    Modifier.padding(VaultSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title") },
                        enabled = !state.isSaving,
                        singleLine = true,
                        shape = VaultShapes.compact,
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                        Box {
                            OutlinedButton(
                                onClick = { typeMenu = true },
                                enabled = !state.isSaving
                            ) {
                                Text(
                                    state.contentType.name.lowercase()
                                        .replaceFirstChar { it.uppercase() }
                                )
                            }
                            DropdownMenu(typeMenu, onDismissRequest = { typeMenu = false }) {
                                ContentType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                type.name.lowercase()
                                                    .replaceFirstChar { it.uppercase() }
                                            )
                                        },
                                        onClick = {
                                            viewModel.onTypeChange(type)
                                            typeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            OutlinedButton(
                                onClick = { categoryMenu = true },
                                enabled = !state.isSaving
                            ) {
                                Text(
                                    state.categories.firstOrNull { it.id == state.categoryId }?.name
                                        ?: "No category"
                                )
                            }
                            DropdownMenu(
                                categoryMenu,
                                onDismissRequest = { categoryMenu = false }
                            ) {
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
                            OutlinedButton(
                                onClick = { folderMenu = true },
                                enabled = !state.isSaving
                            ) {
                                Text(
                                    state.folders.firstOrNull { it.id == state.folderId }?.name
                                        ?: "No folder"
                                )
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
                        enabled = !state.isSaving,
                        shape = VaultShapes.compact,
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::onContentChange,
                label = { Text("Content") },
                enabled = !state.isSaving,
                shape = VaultShapes.panel,
                colors = glassTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = if (state.contentType == ContentType.CODE) {
                        FontFamily.Monospace
                    } else {
                        FontFamily.Default
                    },
                    fontSize = state.fontSize.sp,
                    lineHeight = (state.fontSize + state.lineSpacing).sp
                ),
                keyboardOptions = KeyboardOptions.Default,
                maxLines = 2000
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.wordCount} words • ${state.characterCount} characters • " +
                        "${state.lineCount} lines" +
                        if (state.draftSaved && state.autoSave) " • Draft saved" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
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
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
            ) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (state.isSaving) "Saving…" else "Save")
                }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { viewModel.duplicate() },
                        enabled = !state.isSaving
                    ) { Text("Duplicate") }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.isSaving
                    ) {
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
                scope.launch {
                    val deleted = viewModel.delete()
                    if (deleted) {
                        snackbarHostState.showSnackbar("Item deleted")
                        onDeleted()
                    } else {
                        snackbarHostState.showSnackbar(
                            viewModel.state.value.deleteError ?: "The item could not be deleted."
                        )
                    }
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
