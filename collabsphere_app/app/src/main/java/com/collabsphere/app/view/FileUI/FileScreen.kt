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
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
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
            .background(PrintPaper)
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MisregisteredTitle(text = "Files", style = PrintType.H2)
                    PrintChip(text = "${localFiles?.size ?: "…"} files", fill = PrintCoral)
                }
                Spacer(Modifier.width(12.dp))
                SkeuoHeaderActionButton(
                    text = "Upload",
                    icon = Icons.Default.Add,
                    onClick = { showUploadDialog = true }
                )
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
                            title = "No files yet",
                            description = "Tap “Upload” to share documents, images and project files with your team.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        val entranceRegistry = rememberEntranceRegistry()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(state, key = { _, it -> it.id }) { index, file ->
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
                                    modifier = Modifier
                                        .animateItem()
                                        .printListEntrance(file.id, index, entranceRegistry)
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
                    .background(PrintInk.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard)),
                    contentAlignment = Alignment.Center
                ) {
                    CollabSpinner(strokeWidth = 3.dp)
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
    val press = rememberPrintPress(interactionSource)
    val tagShape = remember(file.id) { BlobShape(seed = file.id.toInt(), wobble = 0.14f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), pressProgress = press)
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .printSurface(shape = tagShape, fill = PrintCoral, shadow = null),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = PrintType.H3,
                        color = PrintInk,
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
                        style = PrintType.Caption,
                        color = if (file.url.isEmpty()) PrintInk else Muted,
                        fontWeight = if (file.url.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            SkeuoActionIconButton(
                onClick = deleteFile,
                icon = Icons.Default.DeleteOutline,
                contentDescription = "Delete ${file.fileName}",
                tint = DestructiveStart,
                size = 40.dp,
                iconSize = 20.dp
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
        containerColor = PrintWhite,
        scrimColor = PrintInk.copy(alpha = 0.45f),
        dragHandle = { PrintSheetHandle() },
        shape = PrintSheetShape,
        modifier = Modifier.printSheetOutline()
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
                            .size(48.dp)
                            .printSurface(shape = BlobShape(seed = 13, wobble = 0.14f), fill = PrintCoral, shadow = null),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Upload file",
                            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = PrintInk
                        )
                        Text(
                            text = "Share documents and media with your workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                PrintIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    fill = PrintPaper,
                    size = 40.dp,
                    iconSize = 18.dp,
                    shadow = false
                )
            }

            // SECTION: FILE SELECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SELECT FILE",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                if (selectedFileName.isEmpty()) {
                    // Tap to choose drop/select zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrintPaper)
                            .border(2.dp, PrintInk, RoundedCornerShape(16.dp))
                            .clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .printSurface(shape = CircleShape, fill = PrintSky, shadow = null, outline = 1.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = PrintInk,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Tap to choose a file",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Text(
                                text = "Documents, images, zips, code and media",
                                style = PrintType.Caption,
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
                            .background(PrintPaper)
                            .border(1.5.dp, PrintInk, RoundedCornerShape(16.dp))
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
                                    .printSurface(shape = RoundedCornerShape(12.dp), fill = PrintCoral, shadow = null, outline = 1.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = PrintInk,
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
                                    style = PrintType.Caption,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            PrintChip(text = "Change", fill = PrintWhite)
                        }
                    }
                }
            }

            // INFO CARD: STORAGE POLICY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PrintTokens.RadiusSmall))
                    .background(PrintPaper)
                    .border(1.5.dp, PrintInk, RoundedCornerShape(PrintTokens.RadiusSmall))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Files are stored securely and everyone in this workspace can open them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val canUpload = selectedFileName.isNotEmpty() && !isUploading
            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = "Upload file",
                loadingText = "Uploading…",
                colors = listOf(PrintInk),
                enabled = canUpload,
                loading = isUploading,
                onClick = {
                    val finalFile = selectedFileObject
                    if (finalFile != null && finalFile.exists()) {
                        viewModel.uploadPhysicalFile(finalFile, detectedMimeType)
                        onDismiss()
                    } else {
                        AppToast.error("Please select a valid file first.")
                    }
                }
            )
            PrintButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                enabled = !isUploading,
                modifier = Modifier.fillMaxWidth()
            )
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
    com.collabsphere.app.view.components.PrintConfirmDialog(
        title = "Delete “$fileName”?",
        message = "It will be removed for everyone in the workspace.",
        confirmLabel = "Delete file",
        icon = Icons.Default.DeleteOutline,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
