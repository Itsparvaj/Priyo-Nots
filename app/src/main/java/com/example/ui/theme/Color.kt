package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors derived from the Priyo Nots emblem
val RoyalIndigo = Color(0xFF3C02BE)
val DeepRoyalPurple = Color(0xFF260263)
val RoyalVioletSoft = Color(0xFF6B3DF5)
val CrownGold = Color(0xFFFFB703)
val CrownGoldDeep = Color(0xFFD97706)
val LeafEmerald = Color(0xFF16A34A)
val LeafEmeraldBright = Color(0xFF22C55E)

// Light palette surfaces
val LightBackground = Color(0xFFFAF8FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1ECFE)
val LightOnSurface = Color(0xFF191528)
val LightOnSurfaceVariant = Color(0xFF5D5675)
val LightOutline = Color(0xFFDCD4F2)

// Dark palette surfaces
val DarkBackground = Color(0xFF110E1B)
val DarkSurface = Color(0xFF1A1629)
val DarkSurfaceVariant = Color(0xFF25203A)
val DarkOnSurface = Color(0xFFF3F0FF)
val DarkOnSurfaceVariant = Color(0xFFB3ABD0)
val DarkOutline = Color(0xFF3A3357)

data class NoteColorOption(
    val key: String,
    val label: String,
    val lightBg: Color,
    val darkBg: Color,
    val lightBorder: Color,
    val darkBorder: Color,
    val swatch: Color
) {
    fun backgroundFor(isDark: Boolean): Color = if (isDark) darkBg else lightBg
    fun borderFor(isDark: Boolean): Color = if (isDark) darkBorder else lightBorder
    fun hexFor(isDark: Boolean): String {
        val c = backgroundFor(isDark)
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }
}

object NoteColorPalette {
    val options = listOf(
        NoteColorOption(
            key = "default",
            label = "Classic",
            lightBg = Color(0xFFFFFFFF),
            darkBg = Color(0xFF1E1A30),
            lightBorder = Color(0xFFE5DFF6),
            darkBorder = Color(0xFF352E52),
            swatch = Color(0xFFE5DFF6)
        ),
        NoteColorOption(
            key = "lavender",
            label = "Royal Mist",
            lightBg = Color(0xFFF2ECFF),
            darkBg = Color(0xFF2B1F4E),
            lightBorder = Color(0xFFCBB6FF),
            darkBorder = Color(0xFF573EA1),
            swatch = Color(0xFFB594FF)
        ),
        NoteColorOption(
            key = "amber",
            label = "Crown Gold",
            lightBg = Color(0xFFFFF6DC),
            darkBg = Color(0xFF352811),
            lightBorder = Color(0xFFFCD36A),
            darkBorder = Color(0xFF7D5D1C),
            swatch = Color(0xFFFFC94D)
        ),
        NoteColorOption(
            key = "mint",
            label = "Leaf Mint",
            lightBg = Color(0xFFEAFBF0),
            darkBg = Color(0xFF142E22),
            lightBorder = Color(0xFFA7E8BF),
            darkBorder = Color(0xFF286947),
            swatch = Color(0xFF4ADE80)
        ),
        NoteColorOption(
            key = "sky",
            label = "Sky Ink",
            lightBg = Color(0xFFE9F4FF),
            darkBg = Color(0xFF15273D),
            lightBorder = Color(0xFFAAD3FF),
            darkBorder = Color(0xFF2B5685),
            swatch = Color(0xFF60A5FA)
        ),
        NoteColorOption(
            key = "rose",
            label = "Blush",
            lightBg = Color(0xFFFFEEF3),
            darkBg = Color(0xFF381A27),
            lightBorder = Color(0xFFFBBFD2),
            darkBorder = Color(0xFF7C3552),
            swatch = Color(0xFFF472B6)
        ),
        NoteColorOption(
            key = "slate",
            label = "Graphite",
            lightBg = Color(0xFFF1F3F9),
            darkBg = Color(0xFF232733),
            lightBorder = Color(0xFFCBD2E1),
            darkBorder = Color(0xFF475069),
            swatch = Color(0xFF94A3B8)
        )
    )

    fun resolve(key: String): NoteColorOption =
        options.firstOrNull { it.key == key } ?: options.first()
}
