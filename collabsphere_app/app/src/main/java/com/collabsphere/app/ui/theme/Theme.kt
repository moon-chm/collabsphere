package com.collabsphere.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────
// LIGHT COLOR SCHEME — Modern Skeuomorphic (Warm Parchment)
// ─────────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = PrintInk,
    onPrimary        = PrintPaper,
    primaryContainer = PrintLeaf,
    onPrimaryContainer = PrintInk,
    secondary        = PrintSky,
    onSecondary      = PrintInk,
    secondaryContainer = PrintSky,
    onSecondaryContainer = PrintInk,
    tertiary         = PrintLeaf,
    onTertiary       = PrintInk,
    tertiaryContainer = PrintLeaf,
    onTertiaryContainer = PrintInk,
    error            = PrintRedDark,
    onError          = PrintWhite,
    errorContainer   = PrintCoral,
    onErrorContainer = PrintInk,
    background       = Background,
    onBackground     = Ink,
    surface          = Surface,
    onSurface        = Ink,
    surfaceVariant   = Background,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = PrintWhite,
    surfaceContainerLow = PrintPaper,
    surfaceContainer = PrintWhite,
    surfaceContainerHigh = PrintWhite,
    surfaceContainerHighest = PrintPaper,
    surfaceTint      = PrintPaper,
    outline          = PrintInk,
    outlineVariant   = BorderHairline,
    inverseSurface   = PrintInk,
    inverseOnSurface = PrintPaper,
    scrim            = PrintInk
)

// ─────────────────────────────────────────────────────────────────
// DARK COLOR SCHEME — Modern Skeuomorphic (Deep Charcoal)
// ─────────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = CoralStart,
    onPrimary        = InkDark,
    primaryContainer = CoralEnd,
    secondary        = IndigoEnd,
    onSecondary      = InkDark,
    secondaryContainer = IndigoStart,
    tertiary         = MintGreen,
    onTertiary       = InkDark,
    error            = DestructiveStart,
    onError          = InkDark,
    background       = BackgroundDark,
    onBackground     = InkDark,
    surface          = SurfaceDark,
    onSurface        = InkDark,
    onSurfaceVariant = MutedDark,
    outline          = MutedDark,
    outlineVariant   = BorderHairline
)

// ─────────────────────────────────────────────────────────────────
// THEME ENTRY POINT
// dynamicColor is DISABLED so our custom design tokens always apply
// ─────────────────────────────────────────────────────────────────
@Composable
fun CollabSphereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography   = CollabSphereTypography,
        content      = content
    )
}