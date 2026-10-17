package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import kotlin.math.sin

@Composable
fun WorkspaceAction(
    viewModel: WorkspaceViewModel,
    onNavigateToCreate: () -> Unit
) {
    val blob = remember { BlobShape(seed = 29, wobble = 0.16f) }
    val phase = rememberBlobPhase(durationMillis = 4000)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrintPaper)
            .paperGrain()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .printEntrance(0, alreadyShown = false) {}
                .printSurface(shape = RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(modifier = Modifier.size(120.dp)) {
                HalftonePatch(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(52.dp),
                    color = PrintSky
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(92.dp)
                        .graphicsLayer { translationY = sin(phase()) * 4.dp.toPx() }
                        .printSurface(shape = blob, fill = PrintCoral),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddBusiness,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(38.dp)
                    )
                }
                PrintStar(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    twinkle = true
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MisregisteredTitle(
                    text = "Create a workspace",
                    style = PrintType.H2
                )
                Text(
                    text = "Bring your team, chats, tasks and files into one place.",
                    style = PrintType.Body,
                    color = Muted,
                    textAlign = TextAlign.Center
                )
            }

            PrintButton(
                text = "Create workspace",
                onClick = onNavigateToCreate,
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
