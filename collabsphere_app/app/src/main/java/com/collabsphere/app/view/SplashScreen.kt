package com.collabsphere.app.view

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.BlobShape
import com.collabsphere.app.ui.theme.CollabSphereMark
import com.collabsphere.app.ui.theme.HalftonePatch
import com.collabsphere.app.ui.theme.MisregisteredTitle
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.PrintCoral
import com.collabsphere.app.ui.theme.PrintInk
import com.collabsphere.app.ui.theme.PrintLeaf
import com.collabsphere.app.ui.theme.PrintPaper
import com.collabsphere.app.ui.theme.PrintSky
import com.collabsphere.app.ui.theme.PrintStar
import com.collabsphere.app.ui.theme.PrintTokens
import com.collabsphere.app.ui.theme.PrintType
import com.collabsphere.app.ui.theme.paperGrain
import com.collabsphere.app.ui.theme.printSurface
import com.collabsphere.app.ui.theme.rememberBlobPhase
import com.collabsphere.app.ui.theme.rememberPrintReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val view = LocalView.current
    val reduced = rememberPrintReducedMotion()
    val logo = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            logo.snapTo(1f)
            title.snapTo(1f)
            tagline.snapTo(1f)
            delay(900)
        } else {
            logo.animateTo(1f, tween(600, easing = PrintTokens.SpringEasing))
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            launch { title.animateTo(1f, tween(450, easing = PrintTokens.SpringEasing)) }
            delay(250)
            tagline.animateTo(1f, tween(350))
            delay(700)
        }
        onSplashFinished()
    }

    val phase = rememberBlobPhase(9000)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrintPaper)
            .paperGrain(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-40).dp)
                .size(260.dp)
                .graphicsLayer { rotationZ = phase() * 25f }
                .printSurface(shape = BlobShape(seed = 3, wobble = 0.12f), fill = PrintLeaf, shadow = null)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-70).dp, y = 60.dp)
                .size(220.dp)
                .graphicsLayer { rotationZ = -phase() * 25f }
                .printSurface(shape = BlobShape(seed = 11, wobble = 0.14f), fill = PrintSky, shadow = null)
        )
        HalftonePatch(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = (-90).dp)
                .size(140.dp)
        )
        PrintStar(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 48.dp, y = 140.dp)
                .size(24.dp),
            color = PrintInk,
            twinkle = true
        )
        PrintStar(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-56).dp, y = (-220).dp)
                .size(16.dp),
            color = PrintCoral,
            twinkle = true
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CollabSphereMark(size = 128.dp)

            Spacer(Modifier.height(12.dp))

            if (title.value > 0f) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = title.value.coerceIn(0f, 1f)
                        translationY = (1f - title.value) * 16.dp.toPx()
                    }
                ) {
                    MisregisteredTitle(text = "CollabSphere", style = PrintType.H1.copy(fontSize = 40.sp, lineHeight = 44.sp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Where teams think together.",
                style = PrintType.Body,
                color = Muted,
                modifier = Modifier.graphicsLayer { alpha = tagline.value }
            )
        }
    }
}
