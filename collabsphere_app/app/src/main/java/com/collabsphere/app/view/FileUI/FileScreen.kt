package com.collabsphere.app.view.FileUI

import com.collabsphere.app.view.components.CollabSpinner
import android.util.Log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.collabsphere.app.view.components.AppToast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.collabsphere.app.view.components.FileSkeletonList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import com.collabsphere.app.model.file.FileEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.file.FileViewModel
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
                Log.e("FileScreen", "Operation failed", e)
            }
        }
    }

    if (fileEntity.url.isEmpty()) {
        AppToast.show("File is currently queued for offline upload.")
        return
    }

    onLoadingStateChange(true)
    val cleanFileName = fileEntity.fileName.replace("\\s+".toRegex(), "_")
    val targetFile = File(context.cacheDir, "view_${System.currentTimeMillis()}_$cleanFileName")
    viewModel.downloadFile(fileEntity.url, targetFile) { success ->
        onLoadingStateChange(false)
        if (success) {
            try {
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
                Log.e("FileScreen", "Operation failed", e)
                AppToast.error("No app found to open this type of file.")
            }
        } else {
            AppToast.error("Failed to download file from server.")
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Files count pill
                    Box(
                        modifier = Modifier
                            .drawWithCache {
                                onDrawBehind {
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
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${localFiles?.size ?: "…"} files",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Muted
                        )
                    }

                    // Tactile Header Action Button
                    SkeuoHeaderActionButton(
                        text = "Upload",
                        icon = Icons.Default.Add,
                        onClick = { showUploadDialog = true }
                    )
                }
            }

            androidx.compose.animation.Crossfade(
                targetState = localFiles,
                label = "filesCrossfade",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { state ->
                when {
                    state == null -> {
                        FileSkeletonList(modifier = Modifier.fillMaxSize())
                    }
                    state.isEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Folder,
                            title = "No files available",
                            description = "Tap 'Add file' below to store documents, images, and project assets in this workspace.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start = 18.dp,
                                end = 18.dp,
                                top = 6.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state, key = { it.id }) { file ->
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
                        .drawWithCache {
onDrawBehind {
                            drawRoundRect(
                                color = SurfaceRaised,
                                cornerRadius = CornerRadius(20.dp.toPx())
                            )
                        }
},
                    contentAlignment = Alignment.Center
                ) {
                    CollabSpinner(color = CoralStart, strokeWidth = 3.dp)
                }
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

            SkeuoActionIconButton(
                onClick = deleteFile,
                icon = Icons.Default.DeleteOutline,
                contentDescription = "Delete file",
                tint = DestructiveStart,
                size = 32.dp,
                iconSize = 16.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadFileBottomSheet(
    viewModel: FileViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isUploading by viewModel.uploadingStatus.collectAsStateWithLifecycle()

    var selectedFileObject by remember { mutableStateOf<File?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var detectedMimeType by remember { mutableStateOf("application/octet-stream") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            var displayName = "Unknown File"
            var size = 0L

            context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: "Unknown File"
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

            detectedMimeType = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
            selectedFileName = displayName
            selectedFileSize = size

            try {
                val stagingDir = File(context.filesDir, "pending_uploads").apply { mkdirs() }
                val tempFile = File(stagingDir, "upload_${System.currentTimeMillis()}_$displayName")
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (tempFile.exists()) {
                    selectedFileObject = tempFile
                    if (selectedFileSize == 0L) {
                        selectedFileSize = tempFile.length()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScreen", "Operation failed", e)
                AppToast.error("Error staging file target local buffer.")
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceRaised,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Muted.copy(alpha = 0.4f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Mint, Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Upload file",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Ink
                        )
                        Text(
                            text = "Share documents and media with your workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // SECTION: FILE SELECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SELECT FILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                if (selectedFileName.isEmpty()) {
                    // Tap to choose drop/select zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .skeuoInset(cornerRadius = 16.dp, depth = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Mint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Mint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Tap to choose a file from device",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Text(
                                text = "Documents, images, zip, code and media supported",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted
                            )
                        }
                    }
                } else {
                    // Selected file preview card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .clickable { filePickerLauncher.launch(arrayOf("*/*")) }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Mint.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = Mint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val formattedSize = if (selectedFileSize > 1024 * 1024) String.format("%.1f MB", selectedFileSize / (1024.0 * 1024.0)) else if (selectedFileSize > 1024) String.format("%.0f KB", selectedFileSize / 1024.0) else "${selectedFileSize} B"
                                Text(
                                    text = "$formattedSize · $detectedMimeType",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = IndigoStart
                            )
                        }
                    }
                }
            }

            // INFO CARD: STORAGE POLICY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Mint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Files are securely stored and accessible to all members of this workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .clickable(enabled = !isUploading, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }

                val canUpload = selectedFileName.isNotEmpty() && !isUploading
                val uploadAlpha = if (canUpload) 1f else 0.65f
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .drawWithCache {
                            onDrawBehind {
                                drawRoundRect(
                                    color = Mint.copy(alpha = 0.28f * uploadAlpha),
                                    topLeft = Offset(0f, 3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Mint.copy(alpha = uploadAlpha),
                                            Color(0xFF059669).copy(alpha = uploadAlpha)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = canUpload) {
                            val finalFile = selectedFileObject
                            if (finalFile != null && finalFile.exists()) {
                                viewModel.uploadPhysicalFile(finalFile, detectedMimeType)
                                onDismiss()
                            } else {
                                AppToast.error("Please select a valid file first.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Upload File",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = Color.White.copy(alpha = if (canUpload) 1f else 0.85f)
                        )
                    }
                }
            }
        }
    }
}

// Backward compatibility alias
@Composable
fun UploadFileDialog(
    viewModel: FileViewModel,
    onDismiss: () -> Unit
) {
    UploadFileBottomSheet(viewModel = viewModel, onDismiss = onDismiss)
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
                .drawWithCache {
onDrawBehind {
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
                            .drawWithCache {
onDrawBehind {
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