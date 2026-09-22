package com.collabsphere.app.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.*

/**
 * Reusable shimmer brush modifier tuned to CollabSphere's warm tactile skeuomorphic palette.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(SkeuoTokens.RadiusSmall)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerColors = listOf(
        Color(0xFFDFD9CE).copy(alpha = 0.50f),
        Color(0xFFFAF8F3).copy(alpha = 0.90f),
        Color(0xFFDFD9CE).copy(alpha = 0.50f)
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 400f, translateAnimation - 400f),
        end = Offset(translateAnimation, translateAnimation)
    )
    this
        .clip(shape)
        .background(brush)
}

/**
 * Skeleton placeholder list for NotificationsScreen.
 */
@Composable
fun NotificationSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .skeuoRaised(cornerRadius = 16.dp)
                    .background(SurfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shimmerEffect(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(14.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(10.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder list for DashboardScreen workspaces.
 */
@Composable
fun WorkspaceSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .skeuoRaised(cornerRadius = 18.dp)
                    .background(SurfaceRaised, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shimmerEffect(CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(16.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(12.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .shimmerEffect(CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder list for ChannelScreen.
 */
@Composable
fun ChannelSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .skeuoRaised(cornerRadius = 16.dp)
                    .background(SurfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shimmerEffect(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.50f)
                                .height(15.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(10.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder list for FileScreen.
 */
@Composable
fun FileSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .skeuoRaised(cornerRadius = 16.dp)
                    .background(SurfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shimmerEffect(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.60f)
                                .height(15.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(95.dp)
                                .height(11.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder list for NotesScreen.
 */
@Composable
fun NotesSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .skeuoRaised(cornerRadius = 16.dp)
                    .background(SurfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shimmerEffect(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(15.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(11.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(10.dp)
                                .shimmerEffect(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder list for MessageScreen.
 */
@Composable
fun MessageSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Incoming message 1
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .width(220.dp)
                .height(48.dp)
                .shimmerEffect(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
        )
        // Outgoing message 1
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .width(160.dp)
                .height(40.dp)
                .shimmerEffect(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
        )
        // Incoming message 2
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .width(260.dp)
                .height(64.dp)
                .shimmerEffect(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
        )
        // Outgoing message 2
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .width(200.dp)
                .height(44.dp)
                .shimmerEffect(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
        )
        // Incoming message 3
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .width(140.dp)
                .height(38.dp)
                .shimmerEffect(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
        )
    }
}
