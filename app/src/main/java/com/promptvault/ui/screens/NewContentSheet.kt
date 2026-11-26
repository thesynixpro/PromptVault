package com.promptvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.promptvault.ui.theme.GlassDivider
import com.promptvault.ui.theme.GlassSurface
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewContentSheet(
    onDismiss: () -> Unit,
    onSelect: (NewContentType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = VaultShapes.panel,
            shadowElevation = 0.dp
        ) {
            Column(Modifier.padding(bottom = VaultSpacing.xl)) {
                Text(
                    "Create something new",
                    modifier = Modifier.padding(
                        horizontal = VaultSpacing.xl,
                        vertical = VaultSpacing.md
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                GlassDivider()
                NewContentType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onSelect(type) }
                            .semantics { role = Role.Button }
                            .padding(horizontal = VaultSpacing.xl, vertical = VaultSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (type) {
                                NewContentType.PROMPT -> Icons.Filled.AutoAwesome
                                NewContentType.TEXT -> Icons.Filled.Description
                                NewContentType.CODE -> Icons.Filled.Code
                                NewContentType.FOLDER -> Icons.Filled.Folder
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.padding(start = VaultSpacing.md)) {
                            Text(type.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                type.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
