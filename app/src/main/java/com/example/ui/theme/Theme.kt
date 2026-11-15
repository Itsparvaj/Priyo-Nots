package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.AppSettingsEntity

private val PriyoLightColors = lightColorScheme(
    primary = RoyalIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = DeepRoyalPurple,
    secondary = CrownGoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0C2),
    onSecondaryContainer = Color(0xFF3E2600),
    tertiary = LeafEmerald,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8F7E3),
    onTertiaryContainer = Color(0xFF05381A),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFEBE5FA),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val PriyoDarkColors = darkColorScheme(
    primary = Color(0xFFB794FF),
    onPrimary = Color(0xFF1F0066),
    primaryContainer = Color(0xFF3B1A8E),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = CrownGold,
    onSecondary = Color(0xFF281800),
    secondaryContainer = Color(0xFF533900),
    onSecondaryContainer = Color(0xFFFFE7A3),
    tertiary = LeafEmeraldBright,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF114D29),
    onTertiaryContainer = Color(0xFFBBF7D0),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2D2746),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val PriyoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun PriyoNotsTheme(
    themeMode: String = AppSettingsEntity.THEME_SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppSettingsEntity.THEME_DARK -> true
        AppSettingsEntity.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) PriyoDarkColors else PriyoLightColors

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PriyoTypography,
            shapes = PriyoShapes,
            content = content
        )
    }
}
