package com.example.rohit_project_challlange.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.rohit_project_challlange.R

// ─────────────────────────────────────────────────────────────────
// FRAUNCES — Serif Display Font (Headings, Titles, Big Numbers)
// ─────────────────────────────────────────────────────────────────
val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_medium,       FontWeight.Medium),
    Font(R.font.fraunces_semibold,     FontWeight.SemiBold),
    Font(R.font.fraunces_bold,         FontWeight.Bold),
    Font(R.font.fraunces_italic,       FontWeight.Normal,  FontStyle.Italic)
)

// ─────────────────────────────────────────────────────────────────
// MANROPE — Sans-Serif UI Font (Buttons, Labels, Body, Chat)
// ─────────────────────────────────────────────────────────────────
val ManropeFamily = FontFamily(
    Font(R.font.manrope_regular,       FontWeight.Normal),
    Font(R.font.manrope_medium,        FontWeight.Medium),
    Font(R.font.manrope_semibold,      FontWeight.SemiBold),
    Font(R.font.manrope_bold,          FontWeight.Bold),
    Font(R.font.manrope_extrabold,     FontWeight.ExtraBold)
)

// ─────────────────────────────────────────────────────────────────
// TYPOGRAPHY SCALE
// Rules: sentence case only, no all-caps, hierarchy by weight/size
// ─────────────────────────────────────────────────────────────────
val CollabSphereTypography = Typography(

    // ── Display ── Onboarding headline, splash title
    displayLarge = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 40.sp,
        lineHeight   = 48.sp,
        letterSpacing = (-0.02).sp
    ),
    displayMedium = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = (-0.01).sp
    ),
    displaySmall = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
        letterSpacing = (-0.01).sp
    ),

    // ── Headline ── Screen titles, card headings, workspace names
    headlineLarge = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 20.sp,
        lineHeight   = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 18.sp,
        lineHeight   = 24.sp
    ),

    // ── Title ── Section headings, tab labels
    titleLarge = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 18.sp,
        lineHeight   = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 16.sp,
        lineHeight   = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp
    ),

    // ── Body ── Chat bubbles, descriptions, list items
    bodyLarge = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp
    ),

    // ── Label ── Buttons, tags, badges, timestamps
    labelLarge = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.01.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.02.sp
    ),
    labelSmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 11.sp,
        lineHeight   = 14.sp,
        letterSpacing = 0.04.sp
    )
)

// Legacy alias — keeps any existing code that references `Typography` working
val Typography = CollabSphereTypography