package com.example.rohit_project_challlange.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens for Modern Tactile Skeuomorphism in CollabSphere.
 * Defined from the mechanical sub-grid scale specified in DESIGN.md.
 */
object SkeuoTokens {
    // Corner Radii scale
    val RadiusSmall: Dp = 12.dp      // Compact elements, input wells, segmented controls
    val RadiusMedium: Dp = 16.dp     // Standard buttons, FABs, secondary plates
    val RadiusLarge: Dp = 18.dp      // Master cards, floating content plates
    val RadiusXLarge: Dp = 24.dp     // Modals, dialogs, hero containers
    val RadiusPill: Dp = 9999.dp     // Circular badges, status pills

    // Depth & Elevation scale
    val DepthInset: Dp = 2.dp        // Debossed / sunken trough depth
    val ElevationRaised: Dp = 3.dp   // Standard card / button extrusion
    val ElevationFloating: Dp = 5.dp // Modals, FABs, suspended toolbars
}
