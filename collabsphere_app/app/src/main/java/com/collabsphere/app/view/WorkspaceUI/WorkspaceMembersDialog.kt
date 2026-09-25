package com.collabsphere.app.view.WorkspaceUI

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.dto.workspace.MemberResponse
import com.collabsphere.app.dto.workspace.WorkspaceRoles
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Mint
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun roleLabel(role: String): String = when (role) {
    WorkspaceRoles.OWNER -> "Owner"
    WorkspaceRoles.ADMIN -> "Admin"
    else -> "Member"
}

private data class PendingConfirmation(val title: String, val message: String, val action: () -> Unit)

@Composable
fun WorkspaceMembersDialog(
    workspaceId: Int,
    currentUserId: Int,
    onInviteClick: () -> Unit,
    onLeftWorkspace: () -> Unit,
    onDismiss: () -> Unit
) {
    val api = koinInject<WorkspaceApiService>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var members by remember { mutableStateOf<List<MemberResponse>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var confirmation by remember { mutableStateOf<PendingConfirmation?>(null) }

    LaunchedEffect(workspaceId, reloadKey) {
        runCatching { api.getWorkspaceMembers(workspaceId) }
            .onSuccess { list ->
                members = list.sortedWith(
                    compareBy<MemberResponse> { listOf(WorkspaceRoles.OWNER, WorkspaceRoles.ADMIN, WorkspaceRoles.MEMBER).indexOf(it.role) }
                        .thenBy { it.userName.lowercase() }
                )
                loadFailed = false
            }
            .onFailure { loadFailed = true }
    }

    val myRole = members?.firstOrNull { it.userId == currentUserId }?.role

    fun runAction(success: String, block: suspend () -> HttpStatusCode, after: () -> Unit = { reloadKey++ }) {
        scope.launch {
            val status = runCatching { block() }.getOrNull()
            val message = when {
                status == null -> "Couldn't reach the server. Try again."
                status.isSuccess() -> success
                status == HttpStatusCode.Forbidden -> "You don't have permission to do that."
                else -> "That didn't work (${status.value})."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (status?.isSuccess() == true) after()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceRaised)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onInviteClick) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = CoralStart, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Invite", color = CoralStart, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                loadFailed && members == null -> Text("Couldn't load members.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                members == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CoralStart, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(members.orEmpty(), key = { it.userId }) { member ->
                        MemberRow(
                            member = member,
                            isSelf = member.userId == currentUserId,
                            myRole = myRole,
                            onPromote = {
                                runAction("${member.userName} is now an admin", { api.setMemberRole(workspaceId, member.userId, WorkspaceRoles.ADMIN) })
                            },
                            onDemote = {
                                runAction("${member.userName} is now a member", { api.setMemberRole(workspaceId, member.userId, WorkspaceRoles.MEMBER) })
                            },
                            onRemove = {
                                confirmation = PendingConfirmation(
                                    title = "Remove ${member.userName}?",
                                    message = "They'll lose access to this workspace's channels, tasks, notes and files."
                                ) {
                                    runAction("${member.userName} was removed", { api.removeMember(workspaceId, member.userId) })
                                }
                            },
                            onLeave = {
                                confirmation = PendingConfirmation(
                                    title = "Leave this workspace?",
                                    message = "You'll need a new invitation to join again."
                                ) {
                                    runAction("You left the workspace", { api.removeMember(workspaceId, currentUserId) }, after = onLeftWorkspace)
                                }
                            }
                        )
                    }
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = Muted)
            }
        }
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    pending.action()
                }) { Text("Confirm", color = DestructiveStart, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("Cancel", color = Muted) }
            }
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberResponse,
    isSelf: Boolean,
    myRole: String?,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onRemove: () -> Unit,
    onLeave: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val canChangeRole = myRole == WorkspaceRoles.OWNER && !isSelf && member.role != WorkspaceRoles.OWNER
    val canRemove = !isSelf && WorkspaceRoles.canRemove(myRole, member.role, isSelf = false)
    val canLeave = isSelf && member.role != WorkspaceRoles.OWNER
    val badgeColor = when (member.role) {
        WorkspaceRoles.OWNER -> CoralStart
        WorkspaceRoles.ADMIN -> IndigoStart
        else -> Mint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.copy(alpha = 0.03f))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "${member.userName} (you)" else member.userName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(member.email, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = roleLabel(member.role),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = badgeColor,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        if (canChangeRole || canRemove || canLeave) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Member actions", tint = Muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canChangeRole && member.role == WorkspaceRoles.MEMBER) {
                        DropdownMenuItem(text = { Text("Make admin") }, onClick = { menuOpen = false; onPromote() })
                    }
                    if (canChangeRole && member.role == WorkspaceRoles.ADMIN) {
                        DropdownMenuItem(text = { Text("Remove admin role") }, onClick = { menuOpen = false; onDemote() })
                    }
                    if (canRemove) {
                        DropdownMenuItem(
                            text = { Text("Remove from workspace", color = DestructiveStart) },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                    if (canLeave) {
                        DropdownMenuItem(
                            text = { Text("Leave workspace", color = DestructiveStart) },
                            onClick = { menuOpen = false; onLeave() }
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}
