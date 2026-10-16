package com.collabsphere.app.view.components

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

fun isVoiceMediaUrl(url: String?): Boolean =
    url != null && url.contains("/voice/") && (url.endsWith(".m4a") || url.endsWith(".mp4") || url.endsWith(".aac") || url.contains("/video/upload/"))

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var startedAt = 0L
        private set

    fun start(): Boolean = runCatching {
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse {
        release()
        false
    }

    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

    fun stop(): File? {
        val r = recorder ?: return null
        val result = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        val out = file
        file = null
        return if (result && System.currentTimeMillis() - startedAt >= 700) out else out?.let { it.delete(); null }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        file?.delete()
        file = null
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}

@Composable
fun VoiceRecordingBar(
    recorder: VoiceRecorder,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(recorder) {
        while (true) {
            elapsed = System.currentTimeMillis() - recorder.startedAt
            levels.add(recorder.level())
            if (levels.size > 36) levels.removeAt(0)
            delay(100)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Discard recording", tint = DestructiveStart)
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(DestructiveStart))
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(elapsed),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Ink
        )
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f).height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            levels.forEach { level ->
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight((0.12f + level * 0.88f).coerceIn(0.12f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(CoralStart.copy(alpha = 0.75f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CoralStart)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice message", tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

object VoicePlayback {
    private var player: MediaPlayer? = null
    private var prepared = false
    var currentUrl by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    fun toggle(url: String) {
        val p = player
        if (currentUrl == url && p != null) {
            if (!prepared) return
            runCatching {
                if (p.isPlaying) {
                    p.pause()
                    isPlaying = false
                } else {
                    p.start()
                    isPlaying = true
                }
            }.onFailure { stop() }
            return
        }
        stop()
        currentUrl = url
        prepared = false
        player = MediaPlayer().apply {
            setOnPreparedListener {
                prepared = true
                runCatching { it.start() }.onSuccess { this@VoicePlayback.isPlaying = true }
                if (com.collabsphere.app.model.media.VoiceNoteCache.cachedFile(url) == null) {
                    com.collabsphere.app.MyApplication.appScope.launch {
                        com.collabsphere.app.model.media.VoiceNoteCache.download(url)
                    }
                }
            }
            setOnCompletionListener {
                this@VoicePlayback.isPlaying = false
                runCatching { it.seekTo(0) }
            }
            setOnErrorListener { _, _, _ ->
                this@VoicePlayback.isPlaying = false
                true
            }
            runCatching {
                setDataSource(com.collabsphere.app.model.media.VoiceNoteCache.playbackSource(url))
                prepareAsync()
            }.onFailure { stop() }
        }
    }

    fun positionMs(url: String): Long =
        if (url == currentUrl && prepared) runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L) else 0L

    fun durationMs(url: String): Long =
        if (url == currentUrl && prepared) runCatching { player?.duration?.toLong()?.takeIf { it > 0 } ?: 0L }.getOrDefault(0L) else 0L

    fun stop() {
        val p = player
        player = null
        prepared = false
        currentUrl = null
        isPlaying = false
        if (p != null) {
            runCatching {
                p.setOnPreparedListener(null)
                p.setOnCompletionListener(null)
                p.setOnErrorListener(null)
                p.reset()
            }
            runCatching { p.release() }
        }
    }
}

@Composable
fun VoiceMessagePlayer(url: String, onDarkBubble: Boolean, modifier: Modifier = Modifier) {
    val active = VoicePlayback.currentUrl == url
    val playing = active && VoicePlayback.isPlaying
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, playing) {
        if (!active) {
            position = 0L
            return@LaunchedEffect
        }
        var waits = 0
        do {
            position = VoicePlayback.positionMs(url)
            VoicePlayback.durationMs(url).takeIf { it > 0 }?.let { duration = it }
            delay(200)
        } while (playing || (duration == 0L && waits++ < 50))
    }
    val tint = if (onDarkBubble) Color.White else CoralStart
    val track = if (onDarkBubble) Color.White.copy(alpha = 0.35f) else CoralStart.copy(alpha = 0.2f)
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier.widthIn(min = 200.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (onDarkBubble) Color.White.copy(alpha = 0.22f) else CoralStart.copy(alpha = 0.12f))
                .clickable { VoicePlayback.toggle(url) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause voice message" else "Play voice message",
                tint = tint
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(track)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(tint)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    active && duration > 0 -> "${formatDuration(position)} / ${formatDuration(duration)}"
                    active -> "Loading…"
                    else -> "Voice message"
                },
                fontSize = 11.sp,
                color = if (onDarkBubble) Color.White.copy(alpha = 0.85f) else Muted
            )
        }
    }
}
