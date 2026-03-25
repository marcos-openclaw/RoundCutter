package com.roundcutter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OrangeAccent,
    onPrimary = OnOrange,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeAccentLight,
    secondary = OrangeAccentLight,
    onSecondary = OnOrange,
    secondaryContainer = Charcoal600,
    onSecondaryContainer = OnSurfaceDark,
    tertiary = OrangeAccentDark,
    onTertiary = OnOrange,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Charcoal400,
    outlineVariant = Charcoal600,
    error = ErrorDark,
    onError = OnErrorDark,
)

@Composable
fun RoundCutterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
