package com.collabsphere.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.collabsphere.app.R

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
        lineHeight   = 42.sp,
        letterSpacing = (-0.02).em
    ),
    displayMedium = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 36.sp,
        letterSpacing = (-0.02).em
    ),
    displaySmall = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 32.sp,
        letterSpacing = (-0.01).em
    ),

    // ── Headline ── Screen titles, card headings, workspace names
    headlineLarge = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 26.sp,
        lineHeight   = 30.sp,
        letterSpacing = (-0.01).em
    ),
    headlineMedium = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = FrauncesFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 20.sp,
        lineHeight   = 26.sp
    ),

    // ── Title ── Section headings, tab labels
    titleLarge = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 18.sp,
        lineHeight   = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 16.sp,
        lineHeight   = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 15.sp,
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
        fontSize     = 15.sp,
        lineHeight   = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 13.sp,
        lineHeight   = 18.sp
    ),

    // ── Label ── Buttons, tags, badges, timestamps
    labelLarge = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 15.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.01.em
    ),
    labelMedium = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 13.sp,
        lineHeight   = 18.sp,
        letterSpacing = 0.01.em
    ),
    labelSmall = TextStyle(
        fontFamily   = ManropeFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.04.em
    )
)

// Legacy alias — keeps any existing code that references `Typography` working
val Typography = CollabSphereTypography

// ─────────────────────────────────────────────────────────────────
// NEWSREADER ALIAS — Stitch design uses Newsreader serif;
// we map it to FrauncesFamily which fills the same editorial role.
// ─────────────────────────────────────────────────────────────────
val NewsreaderFamily = FrauncesFamily