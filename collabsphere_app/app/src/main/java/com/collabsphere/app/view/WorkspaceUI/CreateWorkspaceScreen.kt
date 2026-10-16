package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.ui.theme.CoralEnd
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.view.components.AppToast
import com.collabsphere.app.view.components.SkeuoTextField
import com.collabsphere.app.view.components.WorkspaceDialogButton
import com.collabsphere.app.view.components.WorkspaceDialogCard
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import kotlinx.coroutines.delay

@Composable
fun CreateWorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onBack: () -> Unit
) {
    val workspaceStatus by viewModel.workspaceStatus.collectAsStateWithLifecycle()
    val isCreatingWorkspace by viewModel.isCreatingWorkspace.collectAsStateWithLifecycle()

    val name by viewModel.workspaceName.collectAsStateWithLifecycle()
    val owner by viewModel.workspaceOwner.collectAsStateWithLifecycle()
    val password by viewModel.workspacePassword.collectAsStateWithLifecycle()

    var lastClickTime by remember { mutableStateOf(0L) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isFormValid = name.isNotBlank() && owner.isNotBlank() && password.isNotBlank()

    LaunchedEffect(workspaceStatus) {
        workspaceStatus?.let {
            viewModel.clearWorkspaceStatus()
            if (it.contains("successfully", ignoreCase = true)) {
                successMessage = "Workspace created"
            } else {
                AppToast.show(it)
            }
        }
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(1100)
            onBack()
        }
    }

    val submit = {
        val now = System.currentTimeMillis()
        if (!isCreatingWorkspace && isFormValid && now - lastClickTime > 500L) {
            lastClickTime = now
            viewModel.onCreateWorkspace()
        } else if (!isCreatingWorkspace && !isFormValid) {
            AppToast.warning("Please enter the workspace name, owner, and password")
        }
    }

    WorkspaceDialogCard(
        icon = Icons.Default.AddBusiness,
        accent = CoralStart,
        title = "New workspace",
        subtitle = "A shared home for your team's channels, tasks and files.",
        onClose = onBack,
        closeEnabled = !isCreatingWorkspace,
        successMessage = successMessage
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeuoTextField(
                value = name,
                onValueChange = { viewModel.onNameChange(it) },
                placeholder = "Workspace name",
                enabled = !isCreatingWorkspace,
                leadingIcon = {
                    Icon(Icons.Outlined.Business, contentDescription = null, tint = CoralStart.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                }
            )
            SkeuoTextField(
                value = owner,
                onValueChange = { viewModel.onOwnerChange(it) },
                placeholder = "Owner name",
                enabled = !isCreatingWorkspace,
                leadingIcon = {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = CoralStart.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                }
            )
            SkeuoTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                placeholder = "Workspace password",
                enabled = !isCreatingWorkspace,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = submit,
                leadingIcon = {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = CoralStart.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = if (isPasswordVisible) CoralStart else Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Text(
                    text = "Members need this password to join",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        WorkspaceDialogButton(
            text = "Create workspace",
            loadingText = "Creating…",
            colors = listOf(CoralStart, CoralEnd),
            enabled = isFormValid,
            loading = isCreatingWorkspace,
            onClick = submit
        )
    }
}
