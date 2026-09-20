package com.example.rohit_project_challlange.view.UserUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.rohit_project_challlange.AppConfig
import com.example.rohit_project_challlange.ui.theme.CoralLight
import com.example.rohit_project_challlange.ui.theme.CoralStart
import com.example.rohit_project_challlange.ui.theme.Ink
import com.example.rohit_project_challlange.ui.theme.Muted
import com.example.rohit_project_challlange.ui.theme.Surface
import com.example.rohit_project_challlange.ui.theme.SurfaceRaised
import com.example.rohit_project_challlange.ui.theme.skeuoFloatingCard

/** Resolves a possibly-relative avatar URL (as returned by the server) to a full URL. */
fun resolveAvatarUrl(avatarUrl: String): String =
    if (avatarUrl.startsWith("http")) avatarUrl else "${AppConfig.BASE_URL}$avatarUrl"

/**
 * Shared tactile row for "a person" — used by search results, blocked-users list, and
 * workspace member pickers. [trailing] is a slot for a per-context action (Block button,
 * Unblock button, a chevron, etc.); [subtitle] carries status/email/whatever context wants.
 */
@Composable
fun UserRow(
    avatarUrl: String,
    displayName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 18.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .let {
                if (onClick != null) {
                    it.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else it
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(colors = listOf(SurfaceRaised, Surface))
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initialsFallback: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(colors = listOf(CoralLight, CoralStart))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.trim().take(1).uppercase().ifEmpty { "U" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                if (avatarUrl.isEmpty()) {
                    initialsFallback()
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolveAvatarUrl(avatarUrl))
                            .crossfade(true)
                            .size(with(LocalDensity.current) { 38.dp.roundToPx() })
                            .build(),
                        contentDescription = displayName,
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                        loading = { initialsFallback() },
                        error = { initialsFallback() }
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        maxLines = 1
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
