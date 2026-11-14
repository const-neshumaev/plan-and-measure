package com.planmeasure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral30,
    outline = Neutral50,
    outlineVariant = Neutral70,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Slate80,
    onSecondary = Slate10,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Amber80,
    onTertiary = Amber10,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    background = Neutral07,
    onBackground = Neutral80,
    surface = Neutral07,
    onSurface = Neutral80,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80Muted,
    outline = Neutral60,
    outlineVariant = Neutral30,
)

/** Brand theme. Dynamic color is intentionally not used: the palette is part of the identity. */
@Composable
fun PlanMeasureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PlanMeasureTypography,
        shapes = PlanMeasureShapes,
        content = content,
    )
}
