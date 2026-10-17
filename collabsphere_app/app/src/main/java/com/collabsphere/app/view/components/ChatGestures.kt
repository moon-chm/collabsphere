package com.collabsphere.app.view.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.collabsphere.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val triggerPx = with(LocalDensity.current) { 64.dp.toPx() }
    val maxPx = with(LocalDensity.current) { 96.dp.toPx() }
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (armed) onReply()
                    armed = false
                    scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                },
                onDragCancel = {
                    armed = false
                    scope.launch { offsetX.animateTo(0f) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    val next = (offsetX.value + dragAmount * 0.6f).coerceIn(0f, maxPx)
                    if (next != offsetX.value) change.consume()
                    scope.launch { offsetX.snapTo(next) }
                    val nowArmed = next >= triggerPx
                    if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = nowArmed
                }
            )
        }
    ) {
        val progress = (offsetX.value / triggerPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
                }
                .size(32.dp)
                .clip(CircleShape)
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = PrintInk, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) { content() }
    }
}

@Composable
fun MessageStatusTicks(isRead: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = if (isRead) "Read" else "Sent",
        tint = if (isRead) PrintLeaf else PrintPaper.copy(alpha = 0.75f),
        modifier = modifier.size(14.dp)
    )
}

@Composable
fun NewMessagesDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(PrintCoral.copy(alpha = 0.35f)))
        Text(
            text = "New messages",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = PrintInk,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrintCoral.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(PrintCoral.copy(alpha = 0.35f)))
    }
}

@Composable
fun FullScreenImageViewer(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismissDrag = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val backdropAlpha = (0.95f - abs(dismissDrag.value) / 1200f).coerceIn(0.4f, 0.95f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CollabSpinner(color = PrintInk, modifier = Modifier.size(40.dp))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = { if (scale == 1f) onDismiss() }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            if (newScale > 1f || scale > 1f) {
                                scale = newScale
                                offset = if (newScale == 1f) Offset.Zero else offset + pan
                            } else {
                                scope.launch { dismissDrag.snapTo(dismissDrag.value + pan.y) }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed } && dismissDrag.value != 0f) {
                                    if (abs(dismissDrag.value) > 220f) onDismiss()
                                    else scope.launch { dismissDrag.animateTo(0f) }
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + dismissDrag.value
                    }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                IconButton(onClick = { saveImage(context, url) }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Save image", tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

private fun saveImage(context: Context, url: String) {
    runCatching {
        val name = Uri.parse(url).lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "collabsphere_${System.currentTimeMillis()}.jpg"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "CollabSphere/$name")
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }.onSuccess { AppToast.show("Saving image…") }
        .onFailure { AppToast.error("Couldn't save the image") }
}

@Composable
fun UploadProgressBar(progress: Float, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = PrintInk, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (progress >= 1f) "Processing…" else "Uploading… ${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Ink
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = PrintInk,
                trackColor = PrintInk.copy(alpha = 0.15f)
            )
        }
        IconButton(onClick = onCancel, enabled = progress < 1f) {
            Icon(Icons.Default.Close, contentDescription = "Cancel upload", tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}
