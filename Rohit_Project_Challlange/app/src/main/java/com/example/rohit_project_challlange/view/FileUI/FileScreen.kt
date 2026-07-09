package com.example.rohit_project_challlange.view.FileUI

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.file.FileEntity
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workspace Files",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (localFiles.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files available in this workspace.\nTap 'Add File' to start.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(localFiles, key = { it.id }) { file ->
                        FileItemRow(
                            file = file,
                            onFileClick = {
                                openFile(
                                    context = context,
                                    fileEntity = file,
                                    viewModel = viewModel,
                                    onLoadingStateChange = { isDownloading = it }
                                )
                            },
                            deleteFile = { fileToDelete = file }
                        )
                    }
                }
            }
        }

        if (isDownloading) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showUploadDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Add File")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Upload Local File", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select File from Device")
                }
                if (selectedFileName.isNotEmpty()) {
                    Text(
                        text = "Selected: $selectedFileName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalFile = selectedFileObject
                    if (finalFile != null && finalFile.exists()) {
                        viewModel.uploadPhysicalFile(finalFile, detectedMimeType)
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please select a valid file first.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = selectedFileName.isNotEmpty()
            ) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FileItemRow(
    file: FileEntity,
    onFileClick: () -> Unit,
    deleteFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onFileClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                val syncStatusLabel = if (file.url.isEmpty()) " • Waiting for Network Sync" else ""

                Text(
                    text = "Type: ${file.mimeType.ifEmpty { "Unknown" }} • ${file.sizebytes} Bytes$syncStatusLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (file.url.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            IconButton(onClick = deleteFile) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete File", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete $fileName? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}