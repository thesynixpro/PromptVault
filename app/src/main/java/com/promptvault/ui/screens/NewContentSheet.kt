package com.promptvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Default.AutoAwesome
import androidx.compose.material.icons.Default.Code
import androidx.compose.material.icons.Default.Description
import androidx.compose.material.icons.Default.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewContentSheet(
    onDismiss: () -> Unit,
    onSelect: (NewContentType) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Create something new", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            NewContentType.entries.forEach { type ->
                ListItem(
                    headlineContent = { Text(type.label) },
                    supportingContent = { Text(type.description) },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            when (type) {
                                NewContentType.PROMPT -> Icons.Default.AutoAwesome
                                NewContentType.TEXT -> Icons.Default.Description
                                NewContentType.CODE -> Icons.Default.Code
                                NewContentType.FOLDER -> Icons.Default.Folder
                            },
                            null
                        )
                    },
                    onClick = { onSelect(type) }
                )
            }
        }
    }
}

enum class NewContentType(val label: String, val description: String) {
    PROMPT("New Prompt", "Create an AI prompt with tags and category"),
    TEXT("New Text", "Write a long text document"),
    CODE("New Code Snippet", "Save code with preserved formatting"),
    FOLDER("New Folder", "Organize items into a folder")
}
