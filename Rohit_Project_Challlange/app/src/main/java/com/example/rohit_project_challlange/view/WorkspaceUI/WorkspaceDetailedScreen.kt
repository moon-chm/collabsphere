package com.example.rohit_project_challlange.view.WorkspaceUI

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rohit_project_challlange.model.channels.ChannelEntity
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.view.channel.ChannelScreen
import com.example.rohit_project_challlange.viewmodel.channel.ChannelViewModel
import com.example.rohit_project_challlange.view.TaskUI.TaskScreen
import com.example.rohit_project_challlange.view.FileUI.FileScreen
import com.example.rohit_project_challlange.view.NotesUI.NotesScreen
import com.example.rohit_project_challlange.view.dmUI.DMScreen
import com.example.rohit_project_challlange.viewmodel.file.FileViewModel
import com.example.rohit_project_challlange.viewmodel.notes.NotesViewModel
import com.example.rohit_project_challlange.viewmodel.task.TaskViewModel
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailedScreen(
    workspaceName: String,
    userId: Long,
    workspaceId: Int,
    onBack: () -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onAddMemberSubmit: (email: String) -> Unit,
    channelViewModel: ChannelViewModel,
    taskViewModel: TaskViewModel,
    notesViewModel: NotesViewModel,
    fileViewModel: FileViewModel,
    dmViewModel: DmViewModel,
    workspaceMembers: List<UserEntity>
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberEmailInput by remember { mutableStateOf("") }

    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddMemberDialog = false
                memberEmailInput = ""
            },
            title = {
                Text(
                    text = "Add Member to Workspace",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the email address of the team member you want to add to $workspaceName.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = memberEmailInput,
                        onValueChange = { memberEmailInput = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("user@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = memberEmailInput.trim()
                        if (email.isNotEmpty()) {
                            onAddMemberSubmit(email)
                            showAddMemberDialog = false
                            memberEmailInput = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddMemberDialog = false
                        memberEmailInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workspaceName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Add Member",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Channel tab") },
                    label = { Text("Channel") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.TaskAlt, contentDescription = "Task tab") },
                    label = { Text("Task") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files tab") },
                    label = { Text("Files") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Notes tab") },
                    label = { Text("Notes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Forum, contentDescription = "DM tab") },
                    label = { Text("DM") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                if (selectedTab == 0) {
                    ChannelScreen(
                        viewModel = channelViewModel,
                        onChannelClick = onChannelClick
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                if (selectedTab == 1) {
                    TaskScreen(viewModel = taskViewModel)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                if (selectedTab == 2) {
                    FileScreen(viewModel = fileViewModel)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                if (selectedTab == 3) {
                    NotesScreen(viewModel = notesViewModel)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                if (selectedTab == 4) {
                    DMScreen(
                        viewModel = dmViewModel,
                        workspaceId = workspaceId,
                        currentUserId = userId,
                        onExitModule = { selectedTab = 0 }
                    )
                }
            }
        }
    }
}