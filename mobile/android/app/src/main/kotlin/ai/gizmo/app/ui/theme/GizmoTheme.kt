package ai.gizmo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF141414)
val BgSecondary = Color(0xFF1E1E1E)
val BgTertiary = Color(0xFF282828)
val BgHover = Color(0xFF323232)
val Border = Color(0xFF3D3D3D)
val Accent = Color(0xFFD4A574)
val AccentDim = Color(0xFFB8885A)
val TextPrimary = Color(0xFFECECEC)
val TextSecondary = Color(0xFFA0A0A0)
val TextDim = Color(0xFF666666)
val ThinkingBg = Color(0xFF2A2520)
val ThinkingBorder = Color(0xFFD4A574)
val Success = Color(0xFF5CB77A)
val ErrorColor = Color(0xFFE06060)
val UserMsg = Color(0xFF2B2B2B)

private val GizmoDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = BgPrimary,
    primaryContainer = AccentDim,
    secondary = Accent,
    surface = BgPrimary,
    surfaceContainer = BgSecondary,
    surfaceContainerHigh = BgTertiary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    background = BgPrimary,
    onBackground = TextPrimary,
    error = ErrorColor,
    onError = TextPrimary,
    outline = Border,
    outlineVariant = Border
)

@Composable
fun GizmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GizmoDarkColorScheme,
        content = content
    )
}
