package ai.gizmo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

// Dynamic color accessors — these read from ThemeManager.currentPalette
val BgPrimary: Color get() = ThemeManager.currentPalette.value.bgPrimary
val BgSecondary: Color get() = ThemeManager.currentPalette.value.bgSecondary
val BgTertiary: Color get() = ThemeManager.currentPalette.value.bgTertiary
val BgHover: Color get() = ThemeManager.currentPalette.value.bgHover
val Border: Color get() = ThemeManager.currentPalette.value.border
val Accent: Color get() = ThemeManager.currentPalette.value.accent
val AccentDim: Color get() = ThemeManager.currentPalette.value.accentDim
val TextPrimary: Color get() = ThemeManager.currentPalette.value.textPrimary
val TextSecondary: Color get() = ThemeManager.currentPalette.value.textSecondary
val TextDim: Color get() = ThemeManager.currentPalette.value.textDim
val ThinkingBg: Color get() = ThemeManager.currentPalette.value.thinkingBg
val ThinkingBorder: Color get() = ThemeManager.currentPalette.value.thinkingBorder
val Success: Color get() = ThemeManager.currentPalette.value.success
val ErrorColor: Color get() = ThemeManager.currentPalette.value.error
val UserMsg: Color get() = ThemeManager.currentPalette.value.userMsg

@Composable
fun GizmoTheme(content: @Composable () -> Unit) {
    val palette by ThemeManager.currentPalette

    val colorScheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.bgPrimary,
            primaryContainer = palette.accentDim, secondary = palette.accent,
            surface = palette.bgPrimary, surfaceContainer = palette.bgSecondary,
            surfaceContainerHigh = palette.bgTertiary,
            onSurface = palette.textPrimary, onSurfaceVariant = palette.textSecondary,
            background = palette.bgPrimary, onBackground = palette.textPrimary,
            error = palette.error, onError = palette.textPrimary,
            outline = palette.border, outlineVariant = palette.border
        )
    } else {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.bgPrimary,
            primaryContainer = palette.accentDim, secondary = palette.accent,
            surface = palette.bgPrimary, surfaceContainer = palette.bgSecondary,
            surfaceContainerHigh = palette.bgTertiary,
            onSurface = palette.textPrimary, onSurfaceVariant = palette.textSecondary,
            background = palette.bgPrimary, onBackground = palette.textPrimary,
            error = palette.error, onError = palette.textPrimary,
            outline = palette.border, outlineVariant = palette.border
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
