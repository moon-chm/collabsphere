package com.example.rohit_project_challlange.view.dmUI

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.dm.DmEntity
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DMScreen(
    viewModel: DmViewModel,
    workspaceId: Int,
    currentUserId: Long,
    baseUrl: String = "http://10.238.3.93:8080/api/chat",
    onExitModule: () -> Unit
) {
    val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    var activeChatPartner by remember { mutableStateOf<UserEntity?>(null) }
    var typedText by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<DmEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    val currentPartnerId = activeChatPartner?.id
    val currentMembersList = workspaceMembers

    LaunchedEffect(currentMembersList, currentPartnerId) {
        if (currentPartnerId != null) {
            val updatedPartner = currentMembersList.find { it.id == currentPartnerId }
            if (updatedPartner != null) {
                activeChatPartner = updatedPartner
            }
        }
    }

    DisposableEffect(workspaceId, currentUserId, baseUrl) {
        viewModel.loadWorkspaceMembers(workspaceId)
        viewModel.initWebSocketConnection(baseUrl, currentUserId)
        onDispose {}
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    if (activeChatPartner != null) {
        BackHandler {
            activeChatPartner = null
            viewModel.closeChatSessionUi()
        }
    } else {
        BackHandler {
            onExitModule()
        }
    }

    val partner = activeChatPartner
    if (partner == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = workspaceMembers.filter { it.id != currentUserId.toInt() },
                    key = { it.id }
                ) { member ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeChatPartner = member
                                    viewModel.loadChatHistory(workspaceId, currentUserId.toInt(), member.id, baseUrl)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = member.userName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isOwnMessage = message.senderId.toLong() == currentUserId

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(
                                horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                                                bottomEnd = if (isOwnMessage) 4.dp else 16.dp
                                            )
                                        )
                                        .background(
                                            if (isOwnMessage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (isOwnMessage) {
                                                    selectedMessage = message
                                                    showActionMenu = true
                                                }
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = message.dm_content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (showActionMenu && selectedMessage?.id == message.id) {
                                        DropdownMenu(
                                            expanded = showActionMenu,
                                            onDismissRequest = {
                                                showActionMenu = false
                                                selectedMessage = null
                                            }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Delete Message") },
                                                onClick = {
                                                    viewModel.deleteMessage(message.id, workspaceId)
                                                    showActionMenu = false
                                                    selectedMessage = null
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Icon"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (typedText.trim().isNotEmpty()) {
                                    viewModel.sendMessage(
                                        id = 0,
                                        workspaceId = workspaceId,
                                        senderId = currentUserId.toInt(),
                                        receiverId = partner.id,
                                        content = typedText.trim()
                                    )
                                    typedText = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}