package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.ui.theme.Destructive
import com.collabsphere.app.ui.theme.DestructiveEnd
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.view.components.AppToast
import com.collabsphere.app.view.components.SkeuoTextField
import com.collabsphere.app.view.components.WorkspaceDialogButton
import com.collabsphere.app.view.components.WorkspaceDialogCard
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import kotlinx.coroutines.delay

@Composable
fun DeleteWorkspaceScreen(
    viewModel: WorkspaceViewModel,
    workspaceIdToDelete: Int,
    workspaceNameToDelete: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val workspaceStatus by viewModel.workspaceStatus.collectAsStateWithLifecycle()
    val isDeletingWorkspace by viewModel.isDeletingWorkspace.collectAsStateWithLifecycle()

    val name by viewModel.workspaceName.collectAsStateWithLifecycle()
    val password by viewModel.workspacePassword.collectAsStateWithLifecycle()

    var lastClickTime by remember { mutableStateOf(0L) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isFormValid = name.isNotBlank() && password.isNotBlank()

    LaunchedEffect(workspaceNameToDelete) {
        viewModel.clearInputs()
        viewModel.onNameChange(workspaceNameToDelete)
    }

    LaunchedEffect(workspaceStatus) {
        workspaceStatus?.let {
            viewModel.clearWorkspaceStatus()
            if (it.contains("successfully", ignoreCase = true)) {
                successMessage = "Workspace deleted"
            } else {
                AppToast.show(it)
            }
        }
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(1100)
            onDeleted()
        }
    }

    val submit = {
        val now = System.currentTimeMillis()
        if (!isDeletingWorkspace && isFormValid && now - lastClickTime > 500L) {
            lastClickTime = now
            viewModel.onDeleteWorkspace(workspaceIdToDelete, workspaceNameToDelete)
        } else if (!isDeletingWorkspace && !isFormValid) {
            AppToast.warning("Enter the workspace password to confirm")
        }
    }

    WorkspaceDialogCard(
        icon = Icons.Default.FolderDelete,
        accent = Destructive,
        title = "Delete “$workspaceNameToDelete”?",
        subtitle = "All channels, messages, files, tasks and members will be removed for everyone. This can't be undone.",
        onClose = onBack,
        closeEnabled = !isDeletingWorkspace,
        successMessage = successMessage
    ) {
        SkeuoTextField(
            value = password,
            onValueChange = { viewModel.onPasswordChange(it) },
            placeholder = "Workspace password",
            enabled = !isDeletingWorkspace,
            isError = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = submit,
            leadingIcon = {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = com.collabsphere.app.ui.theme.PrintRedDark, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = com.collabsphere.app.ui.theme.PrintInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        Spacer(Modifier.height(22.dp))

        WorkspaceDialogButton(
            text = "Delete workspace",
            loadingText = "Deleting…",
            colors = listOf(DestructiveStart, DestructiveEnd),
            enabled = isFormValid,
            loading = isDeletingWorkspace,
            onClick = submit
        )

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isDeletingWorkspace, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keep workspace",
                style = com.collabsphere.app.ui.theme.PrintType.Label,
                color = com.collabsphere.app.ui.theme.PrintInk
            )
        }
    }
}
