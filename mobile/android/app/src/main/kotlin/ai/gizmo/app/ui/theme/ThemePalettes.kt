package ai.gizmo.app.ui.theme

import androidx.compose.ui.graphics.Color

data class GizmoColorScheme(
    val bgPrimary: Color, val bgSecondary: Color, val bgTertiary: Color, val bgHover: Color,
    val border: Color, val accent: Color, val accentDim: Color,
    val textPrimary: Color, val textSecondary: Color, val textDim: Color,
    val thinkingBg: Color, val thinkingBorder: Color,
    val success: Color, val error: Color, val codeBg: Color, val userMsg: Color,
    val isLight: Boolean = false, val usePixelFont: Boolean = false, val sharpCorners: Boolean = false
)

val DefaultPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF141414), bgSecondary = Color(0xFF1E1E1E), bgTertiary = Color(0xFF282828), bgHover = Color(0xFF323232),
    border = Color(0xFF3D3D3D), accent = Color(0xFFD4A574), accentDim = Color(0xFFB8885A),
    textPrimary = Color(0xFFECECEC), textSecondary = Color(0xFFA0A0A0), textDim = Color(0xFF666666),
    thinkingBg = Color(0xFF2A2520), thinkingBorder = Color(0xFFD4A574),
    success = Color(0xFF5CB77A), error = Color(0xFFE06060), codeBg = Color(0xFF1A1A1A), userMsg = Color(0xFF2B2B2B))

val NesPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF000000), bgSecondary = Color(0xFF1C1C1C), bgTertiary = Color(0xFF2A2A2A), bgHover = Color(0xFF333333),
    border = Color(0xFF545454), accent = Color(0xFFE40000), accentDim = Color(0xFFB80000),
    textPrimary = Color(0xFFFCFCFC), textSecondary = Color(0xFFB0B0B0), textDim = Color(0xFF606060),
    thinkingBg = Color(0xFF1A0000), thinkingBorder = Color(0xFFE40000),
    success = Color(0xFF00A800), error = Color(0xFFE40000), codeBg = Color(0xFF0A0A0A), userMsg = Color(0xFF1A1A3A),
    usePixelFont = true, sharpCorners = true)

val SnesPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF1A1A2E), bgSecondary = Color(0xFF25254A), bgTertiary = Color(0xFF303066), bgHover = Color(0xFF3A3A7A),
    border = Color(0xFF4A4A8A), accent = Color(0xFFC8A0FF), accentDim = Color(0xFFA080D0),
    textPrimary = Color(0xFFE8E8FF), textSecondary = Color(0xFFA0A0C0), textDim = Color(0xFF606080),
    thinkingBg = Color(0xFF1A1A30), thinkingBorder = Color(0xFFC8A0FF),
    success = Color(0xFF00C878), error = Color(0xFFFF4040), codeBg = Color(0xFF151530), userMsg = Color(0xFF2A2A50),
    usePixelFont = true, sharpCorners = true)

val GbaPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF0A1628), bgSecondary = Color(0xFF142040), bgTertiary = Color(0xFF1C2850), bgHover = Color(0xFF243060),
    border = Color(0xFF304070), accent = Color(0xFF7BB860), accentDim = Color(0xFF5A9040),
    textPrimary = Color(0xFFDCE8D0), textSecondary = Color(0xFFA0B890), textDim = Color(0xFF607850),
    thinkingBg = Color(0xFF0A1A10), thinkingBorder = Color(0xFF7BB860),
    success = Color(0xFF7BB860), error = Color(0xFFD04040), codeBg = Color(0xFF081420), userMsg = Color(0xFF1A2840),
    usePixelFont = true, sharpCorners = true)

val N64Palette = GizmoColorScheme(
    bgPrimary = Color(0xFF1A1A1A), bgSecondary = Color(0xFF252525), bgTertiary = Color(0xFF303030), bgHover = Color(0xFF3A3A3A),
    border = Color(0xFF4A4A4A), accent = Color(0xFFE04040), accentDim = Color(0xFFC03030),
    textPrimary = Color(0xFFF0F0F0), textSecondary = Color(0xFFB0B0B0), textDim = Color(0xFF707070),
    thinkingBg = Color(0xFF1A1A10), thinkingBorder = Color(0xFF40B040),
    success = Color(0xFF40B040), error = Color(0xFFE04040), codeBg = Color(0xFF141414), userMsg = Color(0xFF2A2530))

val GameCubePalette = GizmoColorScheme(
    bgPrimary = Color(0xFF1A1535), bgSecondary = Color(0xFF25204A), bgTertiary = Color(0xFF302A60), bgHover = Color(0xFF3A3570),
    border = Color(0xFF4A4580), accent = Color(0xFF7B6FCF), accentDim = Color(0xFF5A50A0),
    textPrimary = Color(0xFFE8E0FF), textSecondary = Color(0xFFA8A0C0), textDim = Color(0xFF686080),
    thinkingBg = Color(0xFF1A1530), thinkingBorder = Color(0xFF7B6FCF),
    success = Color(0xFF50C878), error = Color(0xFFE05050), codeBg = Color(0xFF151030), userMsg = Color(0xFF252050))

val WiiPalette = GizmoColorScheme(
    bgPrimary = Color(0xFFF0F0F0), bgSecondary = Color(0xFFFFFFFF), bgTertiary = Color(0xFFE8E8E8), bgHover = Color(0xFFD8D8D8),
    border = Color(0xFFC0C0C0), accent = Color(0xFF0088CC), accentDim = Color(0xFF0070A8),
    textPrimary = Color(0xFF1A1A1A), textSecondary = Color(0xFF505050), textDim = Color(0xFF909090),
    thinkingBg = Color(0xFFE8F0FF), thinkingBorder = Color(0xFF0088CC),
    success = Color(0xFF40A040), error = Color(0xFFD04040), codeBg = Color(0xFFF8F8F8), userMsg = Color(0xFFE0E8F0),
    isLight = true)

val SwitchPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF2D2D2D), bgSecondary = Color(0xFF383838), bgTertiary = Color(0xFF454545), bgHover = Color(0xFF505050),
    border = Color(0xFF5A5A5A), accent = Color(0xFFE60012), accentDim = Color(0xFFC0000F),
    textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFB0B0B0), textDim = Color(0xFF707070),
    thinkingBg = Color(0xFF2A2020), thinkingBorder = Color(0xFF00C3E3),
    success = Color(0xFF30C060), error = Color(0xFFE60012), codeBg = Color(0xFF252525), userMsg = Color(0xFF353535))

val DsPalette = GizmoColorScheme(
    bgPrimary = Color(0xFFC8C8C8), bgSecondary = Color(0xFFD8D8D8), bgTertiary = Color(0xFFB8B8B8), bgHover = Color(0xFFB0B0B0),
    border = Color(0xFFA0A0A0), accent = Color(0xFF3070D0), accentDim = Color(0xFF2060B0),
    textPrimary = Color(0xFF1A1A1A), textSecondary = Color(0xFF404040), textDim = Color(0xFF707070),
    thinkingBg = Color(0xFFD0D8E8), thinkingBorder = Color(0xFF3070D0),
    success = Color(0xFF30A030), error = Color(0xFFD03030), codeBg = Color(0xFFC0C0C8), userMsg = Color(0xFFC0D0E0),
    isLight = true)

val ThreeDsPalette = GizmoColorScheme(
    bgPrimary = Color(0xFF1A2A3A), bgSecondary = Color(0xFF203040), bgTertiary = Color(0xFF283848), bgHover = Color(0xFF304050),
    border = Color(0xFF3A4A5A), accent = Color(0xFF00BCD4), accentDim = Color(0xFF009AB0),
    textPrimary = Color(0xFFE0F0F8), textSecondary = Color(0xFF90B0C0), textDim = Color(0xFF506070),
    thinkingBg = Color(0xFF1A2530), thinkingBorder = Color(0xFF00BCD4),
    success = Color(0xFF40C080), error = Color(0xFFE05050), codeBg = Color(0xFF152030), userMsg = Color(0xFF1A3040))

data class ThemeInfo(val key: String, val label: String, val palette: GizmoColorScheme)

val ALL_THEMES = listOf(
    ThemeInfo("default", "Default", DefaultPalette),
    ThemeInfo("nes", "NES", NesPalette),
    ThemeInfo("snes", "SNES", SnesPalette),
    ThemeInfo("gba", "GBA", GbaPalette),
    ThemeInfo("n64", "N64", N64Palette),
    ThemeInfo("gamecube", "GameCube", GameCubePalette),
    ThemeInfo("wii", "Wii", WiiPalette),
    ThemeInfo("switch", "Switch", SwitchPalette),
    ThemeInfo("ds", "DS", DsPalette),
    ThemeInfo("3ds", "3DS", ThreeDsPalette)
)
