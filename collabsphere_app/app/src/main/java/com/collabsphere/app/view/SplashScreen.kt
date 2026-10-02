package com.collabsphere.app.view

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var logoScale by remember { mutableFloatStateOf(0.5f) }
    var logoAlpha by remember { mutableFloatStateOf(0f) }
    var logoOffsetY by remember { mutableFloatStateOf(0f) }

    val textToType = "Collabsphere"
    var typedText by remember { mutableStateOf("") }
    var showCursor by remember { mutableStateOf(true) }

    val animatedLogoScale by animateFloatAsState(
        targetValue = logoScale,
        animationSpec = tween(durationMillis = 800, easing = EaseOutBack),
        label = "logoScale"
    )
    val animatedLogoAlpha by animateFloatAsState(
        targetValue = logoAlpha,
        animationSpec = tween(durationMillis = 800),
        label = "logoAlpha"
    )
    val animatedLogoOffsetY by animateFloatAsState(
        targetValue = logoOffsetY,
        animationSpec = tween(durationMillis = 800, easing = EaseInOutQuad),
        label = "logoOffset"
    )

    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color.Black else Color.White
    val textColor = if (isDark) Color.White else Color.Black

    // Blinking cursor effect
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor = !showCursor
        }
    }

    LaunchedEffect(Unit) {
        // Step 1: scale and fade in
        logoScale = 1f
        logoAlpha = 1f
        delay(800)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        delay(200)

        // Step 2: move slightly up
        logoOffsetY = -100f

        // Step 3: while shifting up, type text
        delay(300)

        for (i in textToType.indices) {
            typedText = textToType.substring(0, i + 1)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            delay(100)
        }

        // Wait a bit to admire the result
        delay(500)
        
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.collabsphere),
            contentDescription = "App Logo",
            modifier = Modifier
                .offset(y = animatedLogoOffsetY.dp)
                .scale(animatedLogoScale)
                .alpha(animatedLogoAlpha)
                .size(150.dp)
        )

        if (typedText.isNotEmpty()) {
            val displayText = if (showCursor) "$typedText|" else typedText
            Text(
                text = displayText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.offset(y = 80.dp) // Below the shifted logo
            )
        }
    }
}
