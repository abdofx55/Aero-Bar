package com.aero.bar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = AeroBlack,
    primaryContainer = ElectricCyanDim,
    secondary = ObsidianCardElevated,
    onSecondary = TextPrimary,
    tertiary = KineticMint,
    background = AeroBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    error = DangerCrimson
)

@Composable
fun AeroBarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

