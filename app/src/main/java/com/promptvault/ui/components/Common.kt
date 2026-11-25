package com.promptvault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptvault.domain.ContentType
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "Back")
                }
                onMenu != null -> IconButton(onClick = onMenu) {
                    Icon(androidx.compose.material.icons.Icons.Default.Menu, "Menu")
                }
            }
        },
        actions = actions
    )
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

@Composable
fun StatTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.labelMedium)
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp, 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(52.dp), MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
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
    Badge(containerColor = color.copy(alpha = 0.16f), contentColor = color) {
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = when (item.contentType) {
                    ContentType.PROMPT -> androidx.compose.material.icons.Icons.Default.AutoAwesome
                    ContentType.TEXT -> androidx.compose.material.icons.Icons.Default.Description
                    ContentType.CODE -> androidx.compose.material.icons.Icons.Default.Code
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    com.promptvault.domain.BackupFormatter.preview(item.content, 110),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(item.contentType.name) })
                    if (item.categoryName.isNotBlank()) {
                        Text(item.categoryName, style = MaterialTheme.typography.labelSmall)
                    }
                    if (item.folderName.isNotBlank()) {
                        Text("• ${item.folderName}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SyncBadge(item.syncStatus)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(item.updatedAt), style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (item.isFavorite) androidx.compose.material.icons.Icons.Default.Star
                    else androidx.compose.material.icons.Icons.Default.StarBorder,
                    contentDescription = "Favorite",
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
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetGesturesEnabled = true) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        Icon(
                            when (action) {
                                ItemAction.DELETE -> androidx.compose.material.icons.Icons.Default.Delete
                                ItemAction.FAVORITE -> androidx.compose.material.icons.Icons.Default.Star
                                ItemAction.MOVE -> androidx.compose.material.icons.Icons.Default.Folder
                                else -> androidx.compose.material.icons.Icons.Default.Edit
                            },
                            null
                        )
                    },
                    onClick = { onAction(action) }
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String = if (timestamp <= 0L) {
    "—"
} else {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
