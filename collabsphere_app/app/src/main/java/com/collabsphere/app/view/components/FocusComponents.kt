package com.collabsphere.app.view.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.focus.FocusRepo
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val MINUTE_MS = 60_000L

@Composable
fun rememberNow(tickMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(tickMs)
        }
    }
    return now
}

fun focusRemainingLabel(until: Long, now: Long): String {
    val minutes = ((until - now + MINUTE_MS - 1) / MINUTE_MS).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min left" else "${minutes / 60} h ${minutes % 60} min left"
}

@Composable
fun FocusModeCard() {
    val repo = koinInject<FocusRepo>()
    val focusUntil by repo.myFocusUntil.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repo.refreshMine() }

    val activeUntil = focusUntil?.takeIf { it > now }

    fun run(block: suspend () -> Result<Long?>) {
        if (busy) return
        busy = true
        scope.launch {
            block().onFailure { AppToast.error(it.localizedMessage ?: "Couldn't update focus") }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Headphones, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp))
            Text(
                text = "Focus mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
        }
        AnimatedContent(
            targetState = activeUntil != null,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "focusCardState"
        ) { focusing ->
            if (focusing && activeUntil != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focusing until ${chatTime(activeUntil)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink
                        )
                        Text(
                            text = "${focusRemainingLabel(activeUntil, now)} · DMs can wait until you're done",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                    FocusChip(label = "End focus", selected = true, enabled = !busy) { run { repo.endFocus() } }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Teammates see that you're focusing and can choose to have their DMs delivered when you're free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("25 min" to 25L, "1 hour" to 60L, "2 hours" to 120L, "4 hours" to 240L).forEach { (label, minutes) ->
                            FocusChip(label = label, selected = false, enabled = !busy) {
                                run { repo.startFocus(System.currentTimeMillis() + minutes * MINUTE_MS) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) IndigoStart.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) IndigoStart else Muted.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) IndigoStart else Ink.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}

@Composable
fun PartnerFocusBanner(
    partnerName: String,
    focusUntil: Long,
    holdMessages: Boolean,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = rememberNow()
    val freeAt = chatTime(focusUntil)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IndigoStart.copy(alpha = 0.08f))
            .border(1.dp, IndigoStart.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Headphones, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(16.dp))
            Text(
                text = "$partnerName is focusing until $freeAt",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = focusRemainingLabel(focusUntil, now),
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChoice(
                label = "Deliver at $freeAt",
                selected = holdMessages,
                modifier = Modifier.weight(1f),
                leading = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = if (holdMessages) Color.White else IndigoStart, modifier = Modifier.size(14.dp)) }
            ) { onHoldChange(true) }
            FocusChoice(label = "Send now", selected = !holdMessages, modifier = Modifier.weight(1f)) { onHoldChange(false) }
        }
    }
}

@Composable
private fun FocusChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) IndigoStart else Color.Transparent)
            .border(1.dp, if (selected) IndigoStart else IndigoStart.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Box(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else IndigoStart,
            maxLines = 1
        )
    }
}
