package io.github.nodyssey

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.nodyssey.ui.settings.theme.toPlaza

/** Neutral desktop surfaces keep the list and reader visually continuous. */
private fun desktopNeutralScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color(0xFF93B4FF), onPrimary = Color(0xFF0D2A66),
        primaryContainer = Color(0xFF243D66), onPrimaryContainer = Color(0xFFD8E5FF),
        background = Color(0xFF16191F), onBackground = Color(0xFFE5E7EB),
        surface = Color(0xFF1C2027), onSurface = Color(0xFFE5E7EB),
        surfaceVariant = Color(0xFF29313D), onSurfaceVariant = Color(0xFFADB7C6),
        surfaceContainerLowest = Color(0xFF15181E), surfaceContainerLow = Color(0xFF20252D),
        surfaceContainer = Color(0xFF252B35), surfaceContainerHigh = Color(0xFF2C333E), surfaceContainerHighest = Color(0xFF343D49),
        outline = Color(0xFF626D7D), outlineVariant = Color(0xFF363F4C),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF2563EB), onPrimary = Color.White,
        primaryContainer = Color(0xFFE7EFFF), onPrimaryContainer = Color(0xFF1D4ED8),
        secondary = Color(0xFF52627A), secondaryContainer = Color(0xFFEEF2F7), onSecondaryContainer = Color(0xFF334155),
        tertiary = Color(0xFF2563EB),
        background = Color(0xFFF6F7F9), onBackground = Color(0xFF1F2937),
        surface = Color.White, onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFF1F3F6), onSurfaceVariant = Color(0xFF64748B),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainer = Color(0xFFF1F3F6), surfaceContainerHigh = Color(0xFFECEFF3), surfaceContainerHighest = Color(0xFFE4E8EE),
        surfaceBright = Color.White, surfaceDim = Color(0xFFF1F3F6),
        outline = Color(0xFF94A3B8), outlineVariant = Color(0xFFE2E6EC),
    )
}

internal fun desktopColorScheme(dark: Boolean, accent: androidx.compose.material3.ColorScheme? = null): androidx.compose.material3.ColorScheme {
    val neutral = desktopNeutralScheme(dark)
    return if (accent == null) {
        neutral
    } else {
        neutral.copy(
            primary = accent.primary, onPrimary = accent.onPrimary,
            primaryContainer = accent.primaryContainer, onPrimaryContainer = accent.onPrimaryContainer,
            secondary = accent.secondary, onSecondary = accent.onSecondary,
            secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
            tertiary = accent.tertiary, onTertiary = accent.onTertiary,
            tertiaryContainer = accent.tertiaryContainer, onTertiaryContainer = accent.onTertiaryContainer,
            inversePrimary = accent.inversePrimary,
        )
    }
}

@androidx.compose.runtime.Composable
internal fun rememberDesktopScheme(settings: io.github.nodyssey.data.settings.UserSettings, dark: Boolean): androidx.compose.material3.ColorScheme {
    val seed = io.github.nodyssey.ui.settings.theme.rememberActiveSeed(settings)
    val character = io.github.nodyssey.ui.settings.theme.activeCharacterPalette(settings)
    val style = settings.paletteStyle.toPlaza()
    return androidx.compose.runtime.remember(seed, character, style, dark) {
        desktopColorScheme(dark, character?.colorScheme(dark) ?: io.github.plaza.designsys.theme.plazaSeedColorScheme(Color(seed), dark, style))
    }
}
