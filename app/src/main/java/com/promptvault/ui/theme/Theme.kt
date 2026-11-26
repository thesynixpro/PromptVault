package com.promptvault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.promptvault.data.preferences.AppSettings
import com.promptvault.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1459C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color(0xFF0B3B78),
    secondary = Color(0xFF007C8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8EDF0),
    onSecondaryContainer = Color(0xFF07525C),
    tertiary = Color(0xFF4A5FC1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E3FF),
    onTertiaryContainer = Color(0xFF26357D),
    background = Color(0xFFF5F8FD),
    onBackground = Color(0xFF111C2C),
    surface = Color(0xFFF8FAFE),
    onSurface = Color(0xFF111C2C),
    surfaceVariant = Color(0xFFE3EBF6),
    onSurfaceVariant = Color(0xFF43516A),
    outline = Color(0xFF6B7891),
    outlineVariant = Color(0xFFC3D0E2),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB6FF),
    onPrimary = Color(0xFF082A5B),
    primaryContainer = Color(0xFF173F76),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFF66D3DD),
    onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF0D5058),
    onSecondaryContainer = Color(0xFFC8EDF0),
    tertiary = Color(0xFFAFBAFF),
    onTertiary = Color(0xFF1D2A6A),
    tertiaryContainer = Color(0xFF303C84),
    onTertiaryContainer = Color(0xFFE0E3FF),
    background = Color(0xFF09111D),
    onBackground = Color(0xFFE6EDF8),
    surface = Color(0xFF0E1928),
    onSurface = Color(0xFFE6EDF8),
    surfaceVariant = Color(0xFF1A2A3D),
    onSurfaceVariant = Color(0xFFB7C4D8),
    outline = Color(0xFF8A9AB4),
    outlineVariant = Color(0xFF34465F),
    error = Color(0xFFFFB4AB)
)

@Composable
fun PromptVaultTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val dynamicColor = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSettings provides settings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PromptVaultTypography,
            content = content
        )
    }
}
