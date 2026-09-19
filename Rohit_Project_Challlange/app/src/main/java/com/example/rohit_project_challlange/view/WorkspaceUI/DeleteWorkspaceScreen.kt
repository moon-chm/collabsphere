package com.example.rohit_project_challlange.view.WorkspaceUI

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.workspace.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteWorkspaceScreen(
    viewModel: WorkspaceViewModel,
    workspaceNameToDelete: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val workspaceStatus by viewModel.workspaceStatus.collectAsStateWithLifecycle()

    val name by viewModel.workspaceName.collectAsStateWithLifecycle()
    val password by viewModel.workspacePassword.collectAsStateWithLifecycle()

    var lastClickTime by remember { mutableStateOf(0L) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val isFormValid = name.isNotBlank() && password.isNotBlank()

    LaunchedEffect(workspaceNameToDelete) {
        viewModel.clearInputs()
        viewModel.onNameChange(workspaceNameToDelete)
    }

    LaunchedEffect(workspaceStatus) {
        workspaceStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearWorkspaceStatus()

            if (it.contains("successfully", ignoreCase = true)) {
                onBack()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(42.dp)
                            .drawBehind {
                                drawCircle(
                                    color = ShadowDark.copy(alpha = 0.25f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx())
                                )
                                drawCircle(
                                    color = ShadowLight.copy(alpha = 0.9f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = SurfaceRaised,
                                    radius = size.minDimension / 2f
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Tactile 3D Destructive Badge
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .drawBehind {
                            drawCircle(
                                color = ShadowDark.copy(alpha = 0.30f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x + 3.dp.toPx(), center.y + 4.dp.toPx())
                            )
                            drawCircle(
                                color = ShadowLight.copy(alpha = 0.95f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x - 2.5.dp.toPx(), center.y - 2.5.dp.toPx())
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SurfaceRaised, Surface),
                                start = Offset(0f, 0f),
                                end = Offset(84.dp.value, 84.dp.value)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(DestructiveLight, Destructive),
                                        center = Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.35f),
                                    radius = 8.dp.toPx(),
                                    center = Offset(center.x - 10.dp.toPx(), center.y - 10.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderDelete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Delete workspace",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "This action is permanent and cannot be undone",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Destructive),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Tactile Form Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        DeleteDebossedField(
                            value = name,
                            onValueChange = { viewModel.onNameChange(it) },
                            label = "Workspace name",
                            placeholder = "Workspace name",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = Destructive,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )

                        DeleteDebossedField(
                            value = password,
                            onValueChange = { viewModel.onPasswordChange(it) },
                            label = "Workspace password",
                            placeholder = "Enter password to confirm",
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Destructive,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        tint = Muted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tactile Destructive Button
                        val deleteInteractionSource = remember { MutableInteractionSource() }
                        val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                        val btnScale by animateFloatAsState(
                            targetValue = if (isDeletePressed) 0.95f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "deleteSubmitScale"
                        )

                        val shadowAlpha = if (isDeletePressed) 0.15f else if (isFormValid) 0.38f else 0.20f
                        val shadowOffset = if (isDeletePressed) 2.dp else 5.dp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                                .drawBehind {
                                    val cr = CornerRadius(16.dp.toPx())
                                    drawRoundRect(
                                        color = DestructiveStart.copy(alpha = shadowAlpha),
                                        topLeft = Offset(0f, shadowOffset.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = cr
                                    )
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.25f),
                                        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = cr
                                    )
                                    drawRoundRect(
                                        brush = Brush.linearGradient(
                                            colors = if (isFormValid) {
                                                listOf(DestructiveStart, DestructiveEnd)
                                            } else {
                                                listOf(DestructiveStart.copy(alpha = 0.72f), DestructiveEnd.copy(alpha = 0.72f))
                                            },
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, size.height)
                                        ),
                                        cornerRadius = cr
                                    )
                                    // Specular hairline highlight hugging rounded contour
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.40f),
                                                Color.White.copy(alpha = 0.12f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = 16.dp.toPx()
                                        ),
                                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                        cornerRadius = cr,
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = deleteInteractionSource,
                                    indication = null
                                ) {
                                    val currentTime = System.currentTimeMillis()
                                    if (isFormValid && currentTime - lastClickTime > 500L) {
                                        lastClickTime = currentTime
                                        viewModel.onDeleteWorkspace()
                                    } else if (!isFormValid) {
                                        Toast.makeText(context, "Please confirm workspace deletion", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Delete workspace",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

/** Inset debossed field tailored for delete workspace confirmation using shared skeuoInset */
@Composable
private fun DeleteDebossedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Ink.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(10.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    cursorBrush = SolidColor(DestructiveStart),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink.copy(alpha = 0.65f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }
    }
}