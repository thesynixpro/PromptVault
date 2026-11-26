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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.promptvault.domain.Category
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.components.formatDate
import com.promptvault.ui.theme.GlassCard
import com.promptvault.ui.theme.GlassFab
import com.promptvault.ui.theme.VaultScaffold
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import com.promptvault.ui.theme.glassTextFieldColors
import com.promptvault.ui.viewmodels.CategoriesViewModel
import com.promptvault.ui.viewmodels.appViewModel

private val categoryColors = listOf(
    0xFF1565C0L, 0xFF00838FL, 0xFF6A1B9AL, 0xFF2E7D32L, 0xFFEF6C00L, 0xFFC2185BL
)

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val viewModel: CategoriesViewModel = appViewModel { CategoriesViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(categoryColors.first()) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    fun openEditor(category: Category?) {
        editing = category
        name = category?.name.orEmpty()
        color = category?.color ?: categoryColors.first()
        showEditor = true
    }

    VaultScaffold(
        topBar = { AppTopBar("Categories", onBack = onBack) },
        floatingActionButton = {
            GlassFab(
                onClick = { openEditor(null) },
                icon = Icons.Filled.Add,
                contentDescription = "New category"
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 88.dp
            )
        ) {
            if (state.categories.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = VaultSpacing.lg)) {
                        EmptyState(
                            "No categories yet",
                            "Create categories to group prompts, text, and code.",
                            Icons.Filled.Category
                        )
                    }
                }
            }
            items(state.categories, key = { it.id }) { category ->
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xs),
                    shape = VaultShapes.compact
                ) {
                    Row(
                        Modifier.padding(VaultSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(Color(category.color).copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Category, null, tint = Color(category.color))
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = VaultSpacing.md)
                        ) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Created ${formatDate(category.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { openEditor(category) }) {
                            Icon(Icons.Filled.Edit, "Edit")
                        }
                        IconButton(onClick = { deleting = category }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editing == null) "New category" else "Edit category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = VaultShapes.compact,
                        colors = glassTextFieldColors()
                    )
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                        categoryColors.forEachIndexed { index, value ->
                            FilterChip(
                                selected = color == value,
                                onClick = { color = value },
                                modifier = Modifier.semantics {
                                    contentDescription = "Color ${index + 1}"
                                },
                                label = { Text("  ") },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .background(Color(value), CircleShape)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        if (editing == null) {
                            viewModel.create(name, color, "category")
                        } else {
                            viewModel.update(editing!!.id, name, color, editing!!.icon)
                        }
                        showEditor = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Cancel") }
            }
        )
    }

    deleting?.let { category ->
        ConfirmDialog(
            title = "Delete category?",
            message = "Items keep their content; they will no longer show this category.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(category.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
