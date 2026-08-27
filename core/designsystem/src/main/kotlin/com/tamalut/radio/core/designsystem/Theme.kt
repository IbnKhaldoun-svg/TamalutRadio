package com.tamalut.radio.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TamalutColors.SandGold,
    onPrimary = Color(0xFF3A2E0C),
    primaryContainer = Color(0xFF514116),
    onPrimaryContainer = TamalutColors.SandGoldLight,
    secondary = TamalutColors.AtlasGreenLight,
    onSecondary = Color(0xFF0B392A),
    secondaryContainer = Color(0xFF234D40),
    onSecondaryContainer = Color(0xFFB7EAD1),
    tertiary = TamalutColors.TerracottaLight,
    onTertiary = Color(0xFF4A1909),
    tertiaryContainer = Color(0xFF69351F),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = TamalutColors.NightBackground,
    onBackground = Color(0xFFE8E3DA),
    surface = TamalutColors.NightSurface,
    onSurface = Color(0xFFE8E3DA),
    surfaceVariant = TamalutColors.NightSurfaceVariant,
    onSurfaceVariant = Color(0xFFC7C0B5),
    outline = Color(0xFF938B7E),
    outlineVariant = Color(0xFF47443E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = TamalutColors.SandGoldDark,
    onPrimary = Color.White,
    primaryContainer = TamalutColors.SandGoldLight,
    onPrimaryContainer = Color(0xFF251A00),
    secondary = TamalutColors.AtlasGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7EAD1),
    onSecondaryContainer = Color(0xFF082019),
    tertiary = TamalutColors.TerracottaDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF3B0A00),
    background = TamalutColors.WarmBackground,
    onBackground = Color(0xFF211B13),
    surface = TamalutColors.WarmSurface,
    onSurface = Color(0xFF211B13),
    surfaceVariant = TamalutColors.WarmSurfaceVariant,
    onSurfaceVariant = Color(0xFF514A40),
    outline = Color(0xFF83786A),
    outlineVariant = Color(0xFFD5C4B4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

@Composable
fun TamalutRadioTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        shapes = TamalutShapes,
        content = content,
    )
}
