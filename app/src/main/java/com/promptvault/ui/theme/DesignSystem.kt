package com.promptvault.ui.theme

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.promptvault.R
import com.promptvault.data.preferences.AppSettings

object VaultSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
}

object VaultShapes {
    val card = RoundedCornerShape(22.dp)
    val panel = RoundedCornerShape(28.dp)
    val compact = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
}

val LocalDarkTheme = compositionLocalOf { false }
val LocalAppSettings = compositionLocalOf { AppSettings() }

@Composable
fun glassSurfaceColor(): Color {
    val base = MaterialTheme.colorScheme.surface
    return if (LocalDarkTheme.current) base.copy(alpha = 0.72f) else base.copy(alpha = 0.86f)
}

@Composable
private fun glassBorderBrush(): Brush {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val highlight = if (LocalDarkTheme.current) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    return Brush.linearGradient(
        colors = listOf(highlight, outline.copy(alpha = 0.42f), outline.copy(alpha = 0.18f)),
        start = Offset.Zero,
        end = Offset.Infinite
    )
}

/**
 * Layered translucent surface with a soft highlight. Blur is used only on a small decorative
 * backdrop, never on text, so readability and scroll performance are preserved on every API level.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = VaultShapes.card,
    shadowElevation: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val sheen = if (LocalDarkTheme.current) {
        Color.White.copy(alpha = 0.045f)
    } else {
        Color.White.copy(alpha = 0.34f)
    }
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(glassSurfaceColor())
            .border(1.dp, glassBorderBrush(), shape)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(sheen, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.9f
                    )
                )
            },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = VaultShapes.card,
    shadowElevation: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClick = onClick).semantics { role = Role.Button }
    } else Modifier
    GlassSurface(
        modifier = modifier.then(clickable),
        shape = shape,
        shadowElevation = shadowElevation,
        content = content
    )
}

@Composable
fun GlassTopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val tint = if (LocalDarkTheme.current) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint, Color.Transparent),
                    startY = 0f,
                    endY = 220f
                )
            )
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                when {
                    onBack != null -> androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            navigationIcon ?: androidx.compose.material.icons.Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    onMenu != null -> androidx.compose.material3.IconButton(onClick = onMenu) {
                        Icon(
                            navigationIcon ?: androidx.compose.material.icons.Icons.Filled.Menu,
                            contentDescription = "Open navigation"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            actions = actions
        )
    }
}

@Composable
fun GlassFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun VaultBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "PromptVault logo",
        modifier = modifier.clip(VaultShapes.compact)
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = VaultSpacing.lg, vertical = VaultSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = VaultSpacing.sm)
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun <T> ScrollableFilterChips(
    items: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = glassSurfaceColor(),
    unfocusedContainerColor = glassSurfaceColor(),
    disabledContainerColor = glassSurfaceColor().copy(alpha = 0.45f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
fun VaultScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            content = content
        )
    }
}

@Composable
private fun BoxScope.AmbientGlow() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = 72.dp, y = (-110).dp)
            .size(280.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = if (LocalDarkTheme.current) 0.16f else 0.12f))
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp)
                else Modifier
            )
    )
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .offset(x = (-90).dp, y = 100.dp)
            .size(260.dp)
            .clip(CircleShape)
            .background(tertiary.copy(alpha = if (LocalDarkTheme.current) 0.12f else 0.10f))
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(72.dp)
                else Modifier
            )
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}
