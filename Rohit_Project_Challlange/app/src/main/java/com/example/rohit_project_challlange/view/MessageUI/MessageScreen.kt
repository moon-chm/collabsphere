package com.example.rohit_project_challlange.view.MessageUI

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.message.MessageEntity
import com.example.rohit_project_challlange.viewmodel.message.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    viewModel: MessageViewModel,
    channelName: String,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageContent by viewModel.messageContent.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "# $channelName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id ?: it.hashCode() }) { message ->
                    val isOwnMessage = message.userId == currentUserId

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = if (isOwnMessage) "You" else message.userName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isOwnMessage) 12.dp else 0.dp,
                                            bottomEnd = if (isOwnMessage) 0.dp else 12.dp
                                        )
                                    )
                                    .background(
                                        if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (selectedMessage == message && showActionMenu) {
                                    DropdownMenu(
                                        expanded = showActionMenu,
                                        onDismissRequest = { showActionMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                showActionMenu = false
                                                isEditing = true
                                                viewModel.onMessageContentChange(message.content)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                showActionMenu = false
                                                message.id?.let { viewModel.onDeleteMessage(it) }
                                                selectedMessage = null
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageContent,
                    onValueChange = { viewModel.onMessageContentChange(it) },
                    placeholder = { Text(if (isEditing) "Edit message..." else "Type a message...") },
                    modifier = Modifier.weight(1f),
                    singleLine = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (isEditing) {
                            val currentMsg = selectedMessage
                            if (currentMsg != null) {
                                viewModel.changeStatus(
                                    currentMsg.copy(content = messageContent.trim()),
                                    currentMsg.status
                                )
                            }
                            isEditing = false
                            selectedMessage = null
                            viewModel.onMessageContentChange("")
                        } else {
                            viewModel.onSendMessageUser()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message"
                    )
                }
            }
        }
    }
}