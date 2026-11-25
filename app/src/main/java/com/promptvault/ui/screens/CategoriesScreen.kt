package com.promptvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Default.Add
import androidx.compose.material.icons.Default.Category
import androidx.compose.material.icons.Default.Delete
import androidx.compose.material.icons.Default.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.promptvault.domain.Category
import com.promptvault.ui.components.AppTopBar
import com.promptvault.ui.components.ConfirmDialog
import com.promptvault.ui.components.EmptyState
import com.promptvault.ui.viewmodels.CategoriesViewModel
import com.promptvault.ui.viewmodels.appViewModel

private val categoryColors = listOf(
    0xFF1565C0, 0xFF00838F, 0xFF6A1B9A, 0xFF2E7D32, 0xFFEF6C00, 0xFFC2185B
)

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val viewModel: CategoriesViewModel = appViewModel { CategoriesViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(categoryColors.first()) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    fun openEditor(category: Category?) {
        editing = category
        name = category?.name.orEmpty()
        color = category?.color ?: categoryColors.first()
        showEditor = true
    }

    Scaffold(
        topBar = { AppTopBar("Categories", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { openEditor(null) }) {
                Icon(Icons.Default.Add, "New category")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        "No categories yet",
                        "Create categories to group prompts, text, and code.",
                        Icons.Default.Category
                    )
                }
            }
            items(state.categories, key = { it.id }) { category ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color(category.color).copy(alpha = 0.12f)
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Category, null, tint = Color(category.color))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text("Created ${com.promptvault.ui.components.formatDate(category.createdAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { openEditor(category) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { deleting = category }) { Icon(Icons.Default.Delete, "Delete") }
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryColors.forEach { value ->
                            androidx.compose.material3.FilterChip(
                                selected = color == value,
                                onClick = { color = value },
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
                        if (editing == null) viewModel.create(name, color, "category")
                        else viewModel.update(editing!!.id, name, color, editing!!.icon)
                        showEditor = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
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
