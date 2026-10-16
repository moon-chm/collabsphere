package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.collabsphere.app.dto.mcp.CreateMcpTokenRequest
import com.collabsphere.app.dto.mcp.CreatedMcpToken
import com.collabsphere.app.dto.mcp.McpActivityItem
import com.collabsphere.app.dto.mcp.McpTokenInfo
import com.collabsphere.app.remote.mcp.McpApiService
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Mint
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object AiToolsFormat {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun ago(timestamp: Long?, now: Long): String {
        if (timestamp == null) return "Never used"
        val diff = (now - timestamp).coerceAtLeast(0)
        return when {
            diff < MINUTE -> "Used just now"
            diff < HOUR -> "Used ${diff / MINUTE}m ago"
            diff < DAY -> "Used ${diff / HOUR}h ago"
            else -> "Used ${diff / DAY}d ago"
        }
    }

    fun expiry(expiresAt: Long?, now: Long): String {
        if (expiresAt == null) return "Never expires"
        val days = ((expiresAt - now) / DAY).coerceAtLeast(0)
        return when {
            days == 0L -> "Expires today"
            days == 1L -> "Expires tomorrow"
            else -> "Expires in $days days"
        }
    }

    fun toolLabel(tool: String): String = when (tool) {
        "list_workspaces" -> "Listed workspaces"
        "workspace_overview" -> "Read workspace overview"
        "list_tasks" -> "Listed tasks"
        "get_task_context" -> "Read a task"
        else -> tool.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun timeShort(timestamp: Long, now: Long): String {
        val diff = (now - timestamp).coerceAtLeast(0)
        return when {
            diff < MINUTE -> "now"
            diff < HOUR -> "${diff / MINUTE}m"
            diff < DAY -> "${diff / HOUR}h"
            else -> "${diff / DAY}d"
        }
    }

    val expiryChoices: List<Pair<String, Int?>> = listOf("30 days" to 30, "90 days" to 90, "1 year" to 365, "Never" to null)
}

@Composable
fun AiToolsCard() {
    val api = koinInject<McpApiService>()
    val scope = rememberCoroutineScope()
    val now = rememberNow(60_000L)
    var tokens by remember { mutableStateOf<List<McpTokenInfo>?>(null) }
    var activity by remember { mutableStateOf<List<McpActivityItem>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<CreatedMcpToken?>(null) }
    var revoking by remember { mutableStateOf<McpTokenInfo?>(null) }

    suspend fun reload() {
        try {
            tokens = api.tokens()
            activity = api.activity(5)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (tokens == null) tokens = emptyList()
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp))
            Text("AI tools (MCP)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
        }
        Text(
            text = "Let Claude Code, Cursor and other AI tools read your workspaces and tasks. Each token acts as you and only sees what you can see.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )

        val list = tokens
        when {
            list == null -> CollabSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = IndigoStart)
            list.isEmpty() -> Text("No tokens yet.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            else -> list.forEach { token ->
                TokenRow(token = token, now = now, onRevoke = { revoking = token })
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(IndigoStart)
                .clickable { creating = true }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text("Create token", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }

        if (activity.isNotEmpty()) {
            Text("Recent activity", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
            activity.forEach { item -> ActivityRow(item = item, now = now) }
        }
    }

    if (creating) {
        CreateTokenDialog(
            onDismiss = { creating = false },
            onCreate = { request ->
                scope.launch {
                    try {
                        created = api.createToken(request)
                        creating = false
                        reload()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        creating = false
                        AppToast.error(e.message ?: "Couldn't create the token")
                    }
                }
            }
        )
    }

    created?.let { result ->
        TokenCreatedDialog(created = result, onDone = { created = null })
    }

    revoking?.let { token ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            containerColor = SurfaceRaised,
            title = { Text("Revoke “${token.name}”?", fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("Any AI tool using this token loses access immediately. This can't be undone.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    scope.launch {
                        runCatching { api.revokeToken(token.id) }
                            .onSuccess { AppToast.show("Token revoked") }
                            .onFailure { AppToast.error("Couldn't revoke the token") }
                        reload()
                    }
                }) { Text("Revoke", color = DestructiveStart, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancel", color = Muted) } }
        )
    }
}

@Composable
private fun TokenRow(token: McpTokenInfo, now: Long, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Muted.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Key, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(token.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${token.prefix}… · ${if ("write" in token.scopes) "Read & write" else "Read only"}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Muted
            )
            Text(
                text = "${AiToolsFormat.ago(token.lastUsedAt, now)} · ${AiToolsFormat.expiry(token.expiresAt, now)}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Revoke ${token.name}", tint = DestructiveStart)
        }
    }
}

@Composable
private fun ActivityRow(item: McpActivityItem, now: Long) {
    val color = when (item.outcome) {
        "ok" -> Mint
        "denied" -> DestructiveStart
        else -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = "${AiToolsFormat.toolLabel(item.tool)} · ${item.tokenName}",
            style = MaterialTheme.typography.bodySmall,
            color = Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(AiToolsFormat.timeShort(item.createdAt, now), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun CreateTokenDialog(onDismiss: () -> Unit, onCreate: (CreateMcpTokenRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var expiryDays by remember { mutableStateOf<Int?>(90) }
    var submitting by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WorkspaceDialogCard(
            icon = Icons.Outlined.Key,
            accent = IndigoStart,
            title = "New AI tool token",
            subtitle = "Name it after where you'll use it, so you can recognize and revoke it later.",
            onClose = onDismiss,
            closeEnabled = !submitting
        ) {
            SkeuoTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = "e.g. Claude Code on my laptop",
                enabled = !submitting
            )
            Spacer(Modifier.height(14.dp))
            Text("Expires", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiToolsFormat.expiryChoices.forEach { (label, days) ->
                    ChoiceChip(label = label, selected = expiryDays == days) { expiryDays = days }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Access", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(label = "Read only", selected = true) { }
                ChoiceChip(label = "Write · coming soon", selected = false, enabled = false) { }
            }
            Spacer(Modifier.height(20.dp))
            WorkspaceDialogButton(
                text = "Create token",
                loadingText = "Creating…",
                colors = listOf(IndigoStart, IndigoStart),
                enabled = name.isNotBlank(),
                loading = submitting,
                onClick = {
                    if (name.isBlank()) {
                        AppToast.warning("Give the token a name first")
                        return@WorkspaceDialogButton
                    }
                    submitting = true
                    onCreate(CreateMcpTokenRequest(name = name.trim(), scopes = listOf("read"), expiresInDays = expiryDays))
                }
            )
        }
    }
}

@Composable
private fun TokenCreatedDialog(created: CreatedMcpToken, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val command = McpApiService.claudeCodeCommand(created.token)
    Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        WorkspaceDialogCard(
            icon = Icons.Outlined.Key,
            accent = Mint,
            title = "Copy your token now",
            subtitle = "For your security it won't be shown again. Treat it like a password.",
            onClose = onDone
        ) {
            CopyBlock(label = "Token", value = created.token) {
                clipboard.setText(AnnotatedString(created.token))
                AppToast.show("Token copied")
            }
            Spacer(Modifier.height(12.dp))
            CopyBlock(label = "Claude Code setup command", value = command) {
                clipboard.setText(AnnotatedString(command))
                AppToast.show("Command copied")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Text(
                    text = "Run the command in a terminal on your computer, then start a new Claude Code session. Revoke the token here any time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
            Spacer(Modifier.height(18.dp))
            WorkspaceDialogButton(text = "I've copied it", colors = listOf(Mint, Mint), onClick = onDone)
        }
    }
}

@Composable
private fun CopyBlock(label: String, value: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Ink)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Muted.copy(alpha = 0.08f))
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label", tint = IndigoStart, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) IndigoStart.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) IndigoStart else Muted.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                !enabled -> Muted.copy(alpha = 0.5f)
                selected -> IndigoStart
                else -> Ink
            }
        )
    }
}
