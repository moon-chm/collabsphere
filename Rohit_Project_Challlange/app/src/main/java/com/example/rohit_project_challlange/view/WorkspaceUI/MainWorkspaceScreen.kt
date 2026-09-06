package com.example.rohit_project_challlange.view.WorkspaceUI

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.workspace.WorkspaceViewModel

@Composable
fun WorkspaceAction(
    viewModel: WorkspaceViewModel,
    onNavigateToCreate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Skeuomorphic tactile card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Soft dark drop shadow
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.28f),
                        topLeft = Offset(6.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )
                    // Specular light highlight (top-left)
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )
                    // Card body
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )
                    // Top hairline bevel highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 28.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Tactile 3D Icon Badge
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .drawBehind {
                            // Dark shadow below badge
                            drawCircle(
                                color = ShadowDark.copy(alpha = 0.35f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x + 3.dp.toPx(), center.y + 4.dp.toPx())
                            )
                            // White specular rim
                            drawCircle(
                                color = ShadowLight.copy(alpha = 0.95f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x - 2.5.dp.toPx(), center.y - 2.5.dp.toPx())
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SurfaceRaised, Surface),
                                start = Offset(0f, 0f),
                                end = Offset(92.dp.value, 92.dp.value)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(CoralLight, CoralStart),
                                        center = Offset(center.x - 6.dp.toPx(), center.y - 6.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                // Specular light highlight
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.38f),
                                    radius = 10.dp.toPx(),
                                    center = Offset(center.x - 12.dp.toPx(), center.y - 12.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddBusiness,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Title & Subtitle in Fraunces serif and Manrope body
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Create workspace",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Set up a brand new workspace environment to begin collaborating on your creative projects.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 22.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tactile CTA Button
                var pressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.96f else 1f,
                    animationSpec = tween(80),
                    label = "createBtnScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .drawBehind {
                            val shadowOffset = if (pressed) 2.dp else 6.dp
                            val shadowAlpha = if (pressed) 0.15f else 0.35f

                            // Dynamic shadow using CoralStart
                            drawRoundRect(
                                color = CoralStart.copy(alpha = shadowAlpha),
                                topLeft = Offset(0f, shadowOffset.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            // White light bounce
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.30f),
                                topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            // Gradient body
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(CoralLight, CoralStart),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            // Top hairline highlight hugging rounded contour
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.40f),
                                        Color.White.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = 16.dp.toPx()
                                ),
                                topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            pressed = true
                            onNavigateToCreate()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Create workspace",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}