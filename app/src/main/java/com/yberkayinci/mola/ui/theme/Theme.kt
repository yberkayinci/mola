package com.yberkayinci.mola.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = MolaGreen,
    onPrimary = MolaWarmSurface,
    primaryContainer = MolaGreenContainer,
    onPrimaryContainer = MolaInk,
    secondary = MolaInkMuted,
    onSecondary = MolaWarmSurface,
    secondaryContainer = MolaWarmSurfaceVariant,
    onSecondaryContainer = MolaInk,
    background = MolaWarmBackground,
    onBackground = MolaInk,
    surface = MolaWarmSurface,
    onSurface = MolaInk,
    surfaceVariant = MolaWarmSurfaceVariant,
    onSurfaceVariant = MolaInkMuted,
    outline = MolaWarmOutline,
    error = MolaError,
    onError = MolaWarmSurface,
    errorContainer = MolaErrorContainer,
    onErrorContainer = MolaInk,
)

private val DarkColorScheme = darkColorScheme(
    primary = MolaGreenDark,
    onPrimary = MolaDarkBackground,
    primaryContainer = MolaGreenContainerDark,
    onPrimaryContainer = MolaDarkInk,
    secondary = MolaDarkInkMuted,
    onSecondary = MolaDarkBackground,
    secondaryContainer = MolaDarkSurfaceVariant,
    onSecondaryContainer = MolaDarkInk,
    background = MolaDarkBackground,
    onBackground = MolaDarkInk,
    surface = MolaDarkSurface,
    onSurface = MolaDarkInk,
    surfaceVariant = MolaDarkSurfaceVariant,
    onSurfaceVariant = MolaDarkInkMuted,
    outline = MolaDarkOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MolaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MolaTypography,
        shapes = MolaShapes,
        content = content,
    )
}
