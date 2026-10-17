package com.collabsphere.app.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.BlobShape
import com.collabsphere.app.ui.theme.HalftonePatch
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.PrintButton
import com.collabsphere.app.ui.theme.PrintCoral
import com.collabsphere.app.ui.theme.PrintInk
import com.collabsphere.app.ui.theme.PrintSky
import com.collabsphere.app.ui.theme.PrintStar
import com.collabsphere.app.ui.theme.PrintType
import com.collabsphere.app.ui.theme.printSurface
import com.collabsphere.app.ui.theme.rememberBlobPhase
import kotlin.math.sin

@Composable
fun SkeuoEmptyState(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val blob = remember(title) { BlobShape(seed = title.hashCode(), wobble = 0.16f) }
    val phase = rememberBlobPhase(durationMillis = 4000)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(132.dp)) {
                HalftonePatch(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(56.dp),
                    color = PrintCoral
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .graphicsLayer { translationY = sin(phase()) * 4.dp.toPx() }
                        .printSurface(shape = blob, fill = PrintSky),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(40.dp)
                    )
                }
                PrintStar(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    color = PrintInk,
                    twinkle = true
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = title,
            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = PrintInk,
            textAlign = TextAlign.Center
        )

        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = PrintType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = Muted,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            PrintButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}
