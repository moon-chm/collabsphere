package com.example.rohit_project_challlange.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────
// MODERN SKEUOMORPHIC DESIGN SYSTEM — CollabSphere
// ─────────────────────────────────────────────────────────────────

// Base Surfaces — Light Mode
val Background       = Color(0xFFE8E4DA)   // darker, more saturated warm grey (was EFEDE7)
val Surface          = Color(0xFFFAF8F3)   // push lighter/whiter (was F6F4EF)
val SurfaceRaised    = Color(0xFFFDFBF7)   // even lighter for modals

// Base Surfaces — Dark Mode
val BackgroundDark   = Color(0xFF1C1A17)
val SurfaceDark      = Color(0xFF242220)
val SurfaceRaisedDark= Color(0xFF2A2825)

// Ink / Text
val Ink              = Color(0xFF2C2A28)   // Primary heading / body text
val Muted            = Color(0xFF7A7570)   // Secondary / supporting text
val InkDark          = Color(0xFFF2F0EA)   // Primary text on dark
val MutedDark        = Color(0xFF9C968C)   // Secondary text on dark

// Border
val BorderHairline   = Color(0x14_2C2A28)  // rgba(44,42,40, 0.08)

// Accents — Coral (Primary CTA)
val CoralStart       = Color(0xFFF0633D)
val CoralEnd         = Color(0xFFD6501F)

// Accents — Indigo (Secondary / Info)
val IndigoStart      = Color(0xFF4C55C4)
val IndigoEnd        = Color(0xFF6B74E0)

// Status Colors
val MintGreen        = Color(0xFF4E9E78)   // Online / Success / Completed
val AmberWarn        = Color(0xFFE0A030)   // Away / Medium priority / Warning
val DestructiveStart = Color(0xFFD64545)   // Delete / Destructive
val DestructiveEnd   = Color(0xFFB23434)

// Semantic Convenience Aliases
val CoralLight       = Color(0xFFFF7A59)
val Mint             = MintGreen
val MintLight        = Color(0xFF66B892)
val Amber            = AmberWarn
val Destructive      = DestructiveStart
val DestructiveLight = Color(0xFFE57373)

// ─────────────────────────────────────────────────────────────────
// DUAL SHADOW COLORS
// Used with drawBehind to simulate physical depth
// ─────────────────────────────────────────────────────────────────
val ShadowLight      = Color(0xD9_FFFFFF)  // rgba(255,255,255, 0.85) — top-left specular
val ShadowDark       = Color(0x8C_A0968A)  // rgba(160,150,138, 0.55) — bottom-right ambient

// Dark Mode Dual Shadows
val ShadowLightDark  = Color(0x0F_FFFFFF)  // rgba(255,255,255, 0.06)
val ShadowDarkDark   = Color(0xA6_000000)  // rgba(0,0,0, 0.65)

// ─────────────────────────────────────────────────────────────────
// LEGACY — kept to avoid breaking any existing references
// ─────────────────────────────────────────────────────────────────
val Purple80         = Color(0xFFD0BCFF)
val PurpleGrey80     = Color(0xFFCCC2DC)
val Pink80           = Color(0xFFEFB8C8)
val Purple40         = Color(0xFF6650a4)
val PurpleGrey40     = Color(0xFF625b71)
val Pink40           = Color(0xFF7D5260)