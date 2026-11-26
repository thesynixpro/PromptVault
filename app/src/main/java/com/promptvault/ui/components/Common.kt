package com.promptvault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptvault.domain.BackupFormatter
import com.promptvault.domain.ContentType
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SyncStatus
import com.promptvault.ui.theme.GlassSurface
import com.promptvault.ui.theme.GlassTopBar
import com.promptvault.ui.theme.LocalAppSettings
import com.promptvault.ui.theme.SectionHeader
import com.promptvault.ui.theme.VaultShapes
import com.promptvault.ui.theme.VaultSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppTopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    GlassTopBar(
        title = title,
        onMenu = onMenu,
        onBack = onBack,
        actions = actions
    )
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    SectionHeader(title = title, action = action)
}

@Composable
fun StatTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = VaultShapes.card) {
        Column(
            Modifier.padding(VaultSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = VaultShapes.panel,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = VaultSpacing.xl, vertical = VaultSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(56.dp)
                        .semantics { contentDescription = title },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action?.invoke()
        }
    }
}

@Composable
fun SyncBadge(status: SyncStatus) {
    val color = when (status) {
        SyncStatus.SYNCED -> Color(0xFF2E7D32)
        SyncStatus.SYNCING, SyncStatus.PENDING, SyncStatus.UPDATE_PENDING -> Color(0xFF0288D1)
        SyncStatus.FAILED -> Color(0xFFC62828)
        SyncStatus.LOCAL_ONLY -> Color(0xFF757575)
    }
    Badge(
        containerColor = color.copy(alpha = 0.16f),
        contentColor = color,
        modifier = Modifier.semantics { contentDescription = "Sync status: ${status.name.replace('_', ' ')}" }
    ) {
        Text(status.name.replace('_', ' '))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: PromptItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hideSensitive = LocalAppSettings.current.hideSensitive
    val typeLabel = when (item.contentType) {
        ContentType.PROMPT -> "Prompt"
        ContentType.TEXT -> "Text"
        ContentType.CODE -> "Code"
    }
    val favoriteLabel = if (item.isFavorite) {
        "Remove ${item.title} from favorites"
    } else {
        "Add ${item.title} to favorites"
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.xs)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = "More actions for ${item.title}") {
                        onLongClick()
                        true
                    }
                )
            }
    ) {
        Row(
            Modifier.padding(VaultSpacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (item.contentType) {
                    ContentType.PROMPT -> Icons.Filled.AutoAwesome
                    ContentType.TEXT -> Icons.Filled.Description
                    ContentType.CODE -> Icons.Filled.Code
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(VaultSpacing.md))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
            ) {
                Text(
                    text = item.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = "$typeLabel: ${item.title.ifBlank { "Untitled" }}" }
                )
                Text(
                    text = if (hideSensitive) {
                        "Content hidden"
                    } else {
                        BackupFormatter.preview(item.content, 110)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(typeLabel) })
                    if (!hideSensitive) {
                        if (item.categoryName.isNotBlank()) {
                            Text(item.categoryName, style = MaterialTheme.typography.labelSmall)
                        }
                        if (item.folderName.isNotBlank()) {
                            Text("• ${item.folderName}", style = MaterialTheme.typography.labelSmall)
                        }
                        item.tags.take(2).forEach { tag ->
                            Text("#$tag", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SyncBadge(item.syncStatus)
                    Spacer(Modifier.width(VaultSpacing.sm))
                    Text(formatDate(item.updatedAt), style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(
                onClick = onFavorite,
                modifier = Modifier.semantics { contentDescription = favoriteLabel }
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = favoriteLabel,
                    tint = if (item.isFavorite) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Cancel") }
        }
    )
}

enum class ItemAction {
    OPEN, EDIT, COPY, DUPLICATE, RENAME, MOVE, FAVORITE, SHARE, DELETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionSheet(
    item: PromptItem,
    onDismiss: () -> Unit,
    onAction: (ItemAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = VaultShapes.panel
        ) {
            Column(
                Modifier
                    .padding(VaultSpacing.lg)
                    .padding(bottom = VaultSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
            ) {
                Text(
                    item.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = VaultSpacing.sm,
                        vertical = VaultSpacing.sm
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listOf(
                    ItemAction.OPEN to "Open",
                    ItemAction.EDIT to "Edit",
                    ItemAction.COPY to "Copy",
                    ItemAction.DUPLICATE to "Duplicate",
                    ItemAction.RENAME to "Rename",
                    ItemAction.MOVE to "Move to folder",
                    ItemAction.FAVORITE to if (item.isFavorite) "Remove favorite" else "Add favorite",
                    ItemAction.SHARE to "Share",
                    ItemAction.DELETE to "Delete"
                ).forEach { (action, label) ->
                    val icon = when (action) {
                        ItemAction.DELETE -> Icons.Filled.Delete
                        ItemAction.FAVORITE -> Icons.Filled.Star
                        ItemAction.MOVE -> Icons.Filled.Folder
                        else -> Icons.Filled.Edit
                    }
                    val tint = if (action == ItemAction.DELETE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onAction(action) }
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = label
                            }
                            .padding(horizontal = VaultSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = tint)
                        Spacer(Modifier.width(VaultSpacing.md))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
                    }
                }
            }
        }
    }
}

fun formatDate(timestamp: Long): String = if (timestamp <= 0L) {
    "—"
} else {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
