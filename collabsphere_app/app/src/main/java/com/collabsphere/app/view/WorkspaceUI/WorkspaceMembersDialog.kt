package com.collabsphere.app.view.WorkspaceUI

import kotlinx.coroutines.flow.catch
import com.collabsphere.app.view.components.AppToast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.collabsphere.app.ui.theme.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import com.collabsphere.app.view.components.CollabSpinner
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

    val memberDirectory = koinInject<com.collabsphere.app.model.workspace.MemberDirectory>()
    LaunchedEffect(workspaceId, reloadKey) {
        memberDirectory.observe(workspaceId, forceRefresh = reloadKey > 0)
            .catch { if (members == null) loadFailed = true }
            .collect { list ->
                members = list.sortedWith(
                    compareBy<MemberResponse> { listOf(WorkspaceRoles.OWNER, WorkspaceRoles.ADMIN, WorkspaceRoles.MEMBER).indexOf(it.role) }
                        .thenBy { it.userName.lowercase() }
                )
                loadFailed = false
            }
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
            AppToast.show(message)
            if (status?.isSuccess() == true) after()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Members",
                    style = PrintType.H2.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    color = PrintInk,
                    modifier = Modifier.weight(1f)
                )
                SkeuoHeaderActionButton(
                    text = "Invite",
                    icon = Icons.Default.GroupAdd,
                    onClick = onInviteClick
                )
            }
            Spacer(Modifier.height(16.dp))

            when {
                loadFailed && members == null -> Text("Couldn't load members.", color = Muted, style = PrintType.Body)
                members == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CollabSpinner(modifier = Modifier.size(28.dp))
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .fadingScrollEdges(listState = listState, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        }

            Spacer(Modifier.height(12.dp))
            PrintButton(
                text = "Close",
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            containerColor = PrintWhite,
            shape = RoundedCornerShape(PrintTokens.RadiusCard),
            modifier = Modifier.border(PrintTokens.Outline, PrintInk, RoundedCornerShape(PrintTokens.RadiusCard)),
            title = { Text(pending.title, style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 26.sp), color = PrintInk) },
            text = { Text(pending.message, style = PrintType.Body, color = PrintInk) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    pending.action()
                }) { Text("Confirm", style = PrintType.Label, color = PrintRedDark) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("Cancel", style = PrintType.Label, color = PrintInk) }
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
        WorkspaceRoles.OWNER -> PrintCoral
        WorkspaceRoles.ADMIN -> PrintSky
        else -> PrintLeaf
    }
    val rowShape = RoundedCornerShape(PrintTokens.RadiusSmall)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(PrintPaper)
            .border(1.5.dp, PrintInk, rowShape)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "${member.userName} (you)" else member.userName,
                style = PrintType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = PrintInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(member.email, style = PrintType.Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PrintChip(text = roleLabel(member.role), fill = badgeColor)
        if (canChangeRole || canRemove || canLeave) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Member actions", tint = PrintInk)
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
                            text = { Text("Remove from workspace", color = PrintRedDark) },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                    if (canLeave) {
                        DropdownMenuItem(
                            text = { Text("Leave workspace", color = PrintRedDark) },
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
