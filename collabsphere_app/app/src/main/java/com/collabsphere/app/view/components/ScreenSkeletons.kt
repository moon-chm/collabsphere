package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.*

/**
 * Reusable shimmer brush modifier tuned to CollabSphere's warm tactile skeuomorphic palette.
 *
 * All shimmering blocks share the same frame clock and are drawn in root
 * coordinates, so the highlight sweeps across the whole screen as a single
 * synchronized band (instead of every block pulsing on its own schedule).
 * Only the draw phase is invalidated per frame — no recomposition.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(SkeuoTokens.RadiusSmall)
): Modifier = composed {
    val progress = produceState(0f) {
        while (true) {
            withFrameMillis { t -> value = (t % SHIMMER_DURATION_MS) / SHIMMER_DURATION_MS.toFloat() }
        }
    }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    this
        .onGloballyPositioned { rootOffset = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            drawRect(ShimmerBase)
            val band = screenWidthPx * 0.45f
            // Travel from fully off-screen left to fully off-screen right.
            val bandStart = -band + (screenWidthPx + band * 2f) * progress.value - band
            // Slight diagonal: shift by the element's vertical position.
            val x0 = bandStart - rootOffset.x - rootOffset.y * 0.15f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, ShimmerHighlight, Color.Transparent),
                    start = Offset(x0, 0f),
                    end = Offset(x0 + band, size.height * 0.35f)
                )
            )
        }
}

private const val SHIMMER_DURATION_MS = 1400L
private val ShimmerBase = Color(0xFFE2DCD1)
private val ShimmerHighlight = Color(0xFFF8F5EF)

/** Text-line placeholder. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).shimmerEffect(RoundedCornerShape(height / 2)))
}

/** Generic raised card with avatar + two lines; building block for list skeletons. */
@Composable
fun SkeletonRowCard(
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    avatarShape: Shape = CircleShape,
    avatarSize: Dp = 40.dp,
    titleFraction: Float = 0.6f,
    subtitleWidth: Dp = 96.dp,
    trailing: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .skeuoRaised(cornerRadius = 16.dp)
            .background(SurfaceRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.size(avatarSize).shimmerEffect(avatarShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.fillMaxWidth(titleFraction).height(14.dp)
                        .shimmerEffect(RoundedCornerShape(7.dp))
                )
                Spacer(Modifier.height(8.dp))
                SkeletonLine(width = subtitleWidth, height = 10.dp)
            }
            if (trailing) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(56.dp).height(28.dp).shimmerEffect(RoundedCornerShape(14.dp)))
            }
        }
    }
}

/** Skeleton for lists of people (blocked users, members, user search). */
@Composable
fun UserListSkeleton(modifier: Modifier = Modifier, count: Int = 5, trailing: Boolean = true) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(count) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(Modifier.size(42.dp).shimmerEffect(CircleShape))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonLine(width = if (i % 2 == 0) 120.dp else 90.dp, height = 14.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonLine(width = if (i % 2 == 0) 80.dp else 110.dp, height = 10.dp)
                }
                if (trailing) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(32.dp).shimmerEffect(CircleShape))
                }
            }
            if (i < count - 1) {
                androidx.compose.material3.Divider(color = ShadowDark.copy(alpha = 0.05f), thickness = 1.dp)
            }
        }
    }
}

/** Skeleton for activity feeds such as GitHub PRs / commits / releases. */
@Composable
fun ActivityListSkeleton(modifier: Modifier = Modifier, count: Int = 5) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) { i ->
            SkeletonRowCard(
                height = 66.dp,
                avatarShape = RoundedCornerShape(10.dp),
                avatarSize = 34.dp,
                titleFraction = if (i % 2 == 0) 0.75f else 0.6f,
                subtitleWidth = 130.dp,
                trailing = true
            )
        }
    }
}

/** Skeleton for a profile page: avatar, name, bio lines and stat chips. */
@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(96.dp).shimmerEffect(CircleShape))
        Spacer(Modifier.height(18.dp))
        SkeletonLine(width = 160.dp, height = 20.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(width = 120.dp, height = 12.dp)
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .skeuoRaised(cornerRadius = 18.dp)
                .background(SurfaceRaised, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(12.dp).shimmerEffect(RoundedCornerShape(6.dp)))
                Box(Modifier.fillMaxWidth(0.75f).height(12.dp).shimmerEffect(RoundedCornerShape(6.dp)))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).shimmerEffect(RoundedCornerShape(6.dp)))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Box(Modifier.width(120.dp).height(44.dp).shimmerEffect(RoundedCornerShape(22.dp)))
            }
        }
    }
}

/** Skeleton for the GitHub tab: header card, stat tiles and an activity feed. */
@Composable
fun GitHubSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .skeuoRaised(cornerRadius = 18.dp)
                .background(SurfaceRaised, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.size(48.dp).shimmerEffect(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(16.dp).shimmerEffect(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.height(8.dp))
                    SkeletonLine(width = 110.dp, height = 11.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .skeuoRaised(cornerRadius = 16.dp)
                        .background(SurfaceRaised, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        SkeletonLine(width = 36.dp, height = 18.dp)
                        Spacer(Modifier.height(8.dp))
                        SkeletonLine(width = 56.dp, height = 10.dp)
                    }
                }
            }
        }
        ActivityListSkeleton(count = 4)
    }
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

/**
 * Skeleton placeholder for the Kanban board in TaskScreen.
 */
@Composable
fun TaskSkeletonList(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (i in 0..2) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .drawBehind {
                            drawRoundRect(
                                color = com.collabsphere.app.ui.theme.ShadowDark.copy(alpha = 0.08f),
                                topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), 4.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                            )
                            drawRoundRect(
                                color = com.collabsphere.app.ui.theme.SurfaceRaised,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                            )
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Column Header Skeleton
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonLine(width = 80.dp, height = 16.dp)
                    SkeletonLine(width = 24.dp, height = 16.dp)
                }
                
                // Cards Skeletons
                for (j in 0..3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .drawBehind {
                                    drawRoundRect(
                                        color = com.collabsphere.app.ui.theme.Surface,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                                    )
                            }
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonLine(width = 160.dp, height = 14.dp)
                            SkeletonLine(width = 100.dp, height = 10.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SkeletonLine(width = 40.dp, height = 12.dp)
                                SkeletonLine(width = 20.dp, height = 20.dp, modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
