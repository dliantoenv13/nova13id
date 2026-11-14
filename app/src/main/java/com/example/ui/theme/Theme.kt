package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaCyanNeon,
    onPrimary = NovaObsidian,
    primaryContainer = NovaSurfaceElevated,
    onPrimaryContainer = NovaCyanNeon,
    secondary = NovaSkyPlasma,
    onSecondary = NovaObsidian,
    secondaryContainer = NovaSurface,
    onSecondaryContainer = NovaSkyPlasma,
    tertiary = NovaTealGlow,
    onTertiary = NovaObsidian,
    background = NovaObsidian,
    onBackground = NovaTextPrimary,
    surface = NovaSurface,
    onSurface = NovaTextPrimary,
    surfaceVariant = NovaSurfaceElevated,
    onSurfaceVariant = NovaTextSecondary,
    outline = NovaBorder,
    outlineVariant = NovaBorder.copy(alpha = 0.5f),
    error = NovaRoseAlert,
    onError = NovaObsidian
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
