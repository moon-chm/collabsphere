package com.example.rohit_project_challlange.ui.theme

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
    primary          = CoralStart,
    onPrimary        = SurfaceRaised,
    primaryContainer = CoralEnd,
    secondary        = IndigoStart,
    onSecondary      = SurfaceRaised,
    secondaryContainer = IndigoEnd,
    tertiary         = MintGreen,
    onTertiary       = SurfaceRaised,
    error            = DestructiveStart,
    onError          = SurfaceRaised,
    background       = Background,
    onBackground     = Ink,
    surface          = Surface,
    onSurface        = Ink,
    onSurfaceVariant = Muted,
    outline          = Muted,
    outlineVariant   = BorderHairline
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

// ─────────────────────────────────────────────────────────────────
// LEGACY ALIAS — keeps existing MainActivity call site compiling
// ─────────────────────────────────────────────────────────────────
@Composable
fun Rohit_Project_ChalllangeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // ignored — always use custom tokens
    content: @Composable () -> Unit
) = CollabSphereTheme(darkTheme = darkTheme, content = content)