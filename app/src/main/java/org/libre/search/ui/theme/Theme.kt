package org.libre.search.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.libre.search.core.Prefs

data class LibreColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val chip: Color,
    val outline: Color,
    val text: Color,
    val textSecondary: Color,
    val link: Color,
    val visited: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val isDark: Boolean,
)

val DarkLibre = LibreColors(
    bg = Color(0xFF1F2023),
    surface = Color(0xFF2A2B2F),
    surface2 = Color(0xFF303134),
    chip = Color(0xFF35363A),
    outline = Color(0xFF3C4043),
    text = Color(0xFFE8EAED),
    textSecondary = Color(0xFF9AA0A6),
    link = Color(0xFF8AB4F8),
    visited = Color(0xFFC58AF9),
    accent = Color(0xFF8AB4F8),
    green = Color(0xFF81C995),
    red = Color(0xFFF28B82),
    yellow = Color(0xFFFDD663),
    isDark = true,
)

val LightLibre = LibreColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF1F3F4),
    surface2 = Color(0xFFE8EAED),
    chip = Color(0xFFF1F3F4),
    outline = Color(0xFFDADCE0),
    text = Color(0xFF202124),
    textSecondary = Color(0xFF4D5156),
    link = Color(0xFF1A0DAB),
    visited = Color(0xFF681DA8),
    accent = Color(0xFF1A73E8),
    green = Color(0xFF188038),
    red = Color(0xFFD93025),
    yellow = Color(0xFFF9AB00),
    isDark = false,
)

val LocalLibre = staticCompositionLocalOf { DarkLibre }

object Brand {
    val blue = Color(0xFF4A8AF4)
    val red = Color(0xFFE8453C)
    val yellow = Color(0xFFF9BB2D)
    val green = Color(0xFF3AA757)
}

@Composable
fun LibreTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Prefs.version.intValue
    val dark = when (Prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkLibre else LightLibre
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, background = c.bg, surface = c.bg, surfaceVariant = c.surface,
        onBackground = c.text, onSurface = c.text, onSurfaceVariant = c.textSecondary, outline = c.outline,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.chip,
        surfaceContainerLow = c.bg, secondaryContainer = c.chip, onSecondaryContainer = c.text,
    ) else lightColorScheme(
        primary = c.accent, background = c.bg, surface = c.bg, surfaceVariant = c.surface,
        onBackground = c.text, onSurface = c.text, onSurfaceVariant = c.textSecondary, outline = c.outline,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.chip,
        surfaceContainerLow = c.bg, secondaryContainer = Color(0xFFD3E3FD), onSecondaryContainer = c.text,
    )
    CompositionLocalProvider(LocalLibre provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
