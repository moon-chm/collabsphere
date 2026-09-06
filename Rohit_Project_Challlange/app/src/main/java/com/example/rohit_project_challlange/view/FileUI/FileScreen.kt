package com.example.rohit_project_challlange.view.FileUI

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.file.FileEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.file.FileViewModel
import java.io.File

fun openFile(
    context: Context,
    fileEntity: FileEntity,
    viewModel: FileViewModel,
    onLoadingStateChange: (Boolean) -> Unit
) {
    val authority = "${context.packageName}.fileprovider"

    if (!fileEntity.localpath.isNullOrEmpty()) {
        val physicalLocalFile = File(fileEntity.localpath)
        if (physicalLocalFile.exists()) {
            try {
                val contentUri = FileProvider.getUriForFile(context, authority, physicalLocalFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, fileEntity.mimeType.ifEmpty { viewModel.getMimeTypeFromExtension(fileEntity.fileName) })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (fileEntity.url.isEmpty()) {
        Toast.makeText(context, "File is currently queued for offline upload.", Toast.LENGTH_SHORT).show()
        return
    }

    onLoadingStateChange(true)
    viewModel.downloadFile(fileEntity.url) { bytes ->
        onLoadingStateChange(false)
        if (bytes != null) {
            try {
                val cleanFileName = fileEntity.fileName.replace("\\s+".toRegex(), "_")
                val targetFile = File(context.cacheDir, "view_${System.currentTimeMillis()}_$cleanFileName")

                targetFile.outputStream().use { output -> output.write(bytes) }

                var resolvedMimeType = viewModel.getMimeTypeFromExtension(cleanFileName)
                if (resolvedMimeType == "application/octet-stream" && fileEntity.mimeType.isNotEmpty()) {
                    resolvedMimeType = fileEntity.mimeType
                }

                val contentUri = FileProvider.getUriForFile(context, authority, targetFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, resolvedMimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "No app found to open this type of file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Failed to download file from server.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun FileScreen(
    viewModel: FileViewModel,
    modifier: Modifier = Modifier
) {
    val localFiles by viewModel.getallfile.collectAsStateWithLifecycle()
    var showUploadDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<FileEntity?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workspace files",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Files count pill
                Box(
                    modifier = Modifier
                        .drawBehind {
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.12f),
                                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(12.dp.toPx())
                            )
                            drawRoundRect(
                                color = SurfaceRaised,
                                cornerRadius = CornerRadius(12.dp.toPx())
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${localFiles.size} files",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }
            }

            if (localFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tactile Empty State Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.20f),
                                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ShadowLight.copy(alpha = 0.85f),
                                    topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = SurfaceRaised,
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                            }
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Mint,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "No files available",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink
                            )

                            Text(
                                text = "Tap 'Add file' below to store documents, images, and project assets in this workspace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 6.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(localFiles, key = { it.id }) { file ->
                        SkeuoFileItemRow(
                            file = file,
                            onFileClick = {
                                openFile(
                                    context = context,
                                    fileEntity = file,
                                    viewModel = viewModel,
                                    onLoadingStateChange = { isDownloading = it }
                                )
                            },
                            deleteFile = { fileToDelete = file },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = SurfaceRaised,
                                cornerRadius = CornerRadius(20.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CoralStart, strokeWidth = 3.dp)
                }
            }
        }

        // Tactile Skeuomorphic FAB
        val fabInteractionSource = remember { MutableInteractionSource() }
        val isFabPressed by fabInteractionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (isFabPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "fileFabScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                .drawBehind {
                    val shadowOffset = if (isFabPressed) 2.dp else 5.dp
                    val shadowAlpha = if (isFabPressed) 0.15f else 0.35f

                    drawRoundRect(
                        color = CoralStart.copy(alpha = shadowAlpha),
                        topLeft = Offset(0f, shadowOffset.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f),
                        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(CoralLight, CoralStart),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    // Top hairline highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 20.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = fabInteractionSource,
                    indication = null
                ) {
                    showUploadDialog = true
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Add file",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
            }
        }

        if (showUploadDialog) {
            UploadFileDialog(
                viewModel = viewModel,
                onDismiss = { showUploadDialog = false }
            )
        }

        fileToDelete?.let { file ->
            DeleteFileConfirmationDialog(
                fileName = file.fileName,
                onConfirm = {
                    viewModel.deleteFile(file.id)
                    fileToDelete = null
                },
                onDismiss = { fileToDelete = null }
            )
        }
    }
}

@Composable
fun SkeuoFileItemRow(
    file: FileEntity,
    onFileClick: () -> Unit,
    deleteFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fileScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 18.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onFileClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File Icon in debossed squircle well
                SkeuoDebossedIconWell(wellSize = 46.dp, cornerRadius = 14.dp) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = Color(0xFF2C221E),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = Color(0xFF1F1A17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val syncStatusLabel = if (file.url.isEmpty()) " • Queued offline" else ""
                    val formattedSize = if (file.sizebytes > 1024 * 1024) {
                        String.format("%.1f MB", file.sizebytes / (1024.0 * 1024.0))
                    } else if (file.sizebytes > 1024) {
                        String.format("%.0f KB", file.sizebytes / 1024.0)
                    } else {
                        "${file.sizebytes} B"
                    }

                    Text(
                        text = "$formattedSize$syncStatusLabel",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = if (file.url.isEmpty()) Amber else Color(0xFF6E635C),
                        fontWeight = if (file.url.isEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = deleteFile,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete file",
                    tint = Destructive.copy(alpha = 0.6f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun UploadFileDialog(
    viewModel: FileViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFileObject by remember { mutableStateOf<File?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var detectedMimeType by remember { mutableStateOf("application/octet-stream") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            var displayName = "Unknown File"

            context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: "Unknown File"
                }
            }

            detectedMimeType = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
            selectedFileName = displayName

            try {
                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$displayName")
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (tempFile.exists()) {
                    selectedFileObject = tempFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error staging file target local buffer.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    // Specular hairline highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 24.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = Mint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Upload file",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                // Select File Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.15f),
                                topLeft = Offset(0f, 2.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                            drawRoundRect(
                                color = Surface,
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = IndigoStart,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Browse device files",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = IndigoStart
                        )
                    }
                }

                if (selectedFileName.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawRoundRect(
                                    color = Background,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Selected: $selectedFileName",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    val canUpload = selectedFileName.isNotEmpty()
                    val btnColor = if (canUpload) CoralStart else Muted.copy(alpha = 0.45f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = btnColor.copy(alpha = 0.25f),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(btnColor, if (canUpload) CoralEnd else btnColor),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canUpload) {
                                val finalFile = selectedFileObject
                                if (finalFile != null && finalFile.exists()) {
                                    viewModel.uploadPhysicalFile(finalFile, detectedMimeType)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Please select a valid file first.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Upload",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteFileConfirmationDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Destructive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Destructive,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Delete file",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = "Are you sure you want to delete $fileName? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = Destructive.copy(alpha = 0.3f),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Destructive, Color(0xFFB52E2E)),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}