package com.collabsphere.app.viewmodel.file
import android.util.Log

import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.file.FileEntity
import com.collabsphere.app.model.file.FileRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FileViewModel(
    private val repo: FileRepo,
    private val loggedUserId: Int,
    private val loggedWorkspaceId: Int,
    private val loggedUserName: String
) : ViewModel() {

    val currentUserId: Int = loggedUserId

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId)
        }
    }

    val getallfile: StateFlow<List<FileEntity>?> =
        repo.getfiles(loggedWorkspaceId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _uploadingStatus = MutableStateFlow<Boolean>(false)
    val uploadingStatus = _uploadingStatus.asStateFlow()

    private val _deletingFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletingFileIds = _deletingFileIds.asStateFlow()

    fun uploadPhysicalFile(selectedFile: File, customMimeType: String? = null) {
        if (!selectedFile.exists() || selectedFile.length() == 0L) return
        if (_uploadingStatus.value) return

        // Set synchronously so rapid picker callbacks cannot enqueue two uploads before launch runs.
        _uploadingStatus.value = true
        viewModelScope.launch {
            try {
                val tentativeEntity = FileEntity(
                    id = 0L,
                    userId = loggedUserId,
                    workspaceId = loggedWorkspaceId,
                    userName = loggedUserName,
                    url = "",
                    mimeType = customMimeType ?: getMimeTypeFromExtension(selectedFile.name),
                    localpath = selectedFile.absolutePath,
                    fileName = selectedFile.name,
                    sizebytes = selectedFile.length(),
                    fileLocation = ""
                )

                repo.uploadfilestoscreen(tentativeEntity)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FileViewModel", "Operation failed", e)
            } finally {
                _uploadingStatus.value = false
            }
        }
    }

    fun downloadFile(url: String, destination: File, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                repo.downloadFileFromServer(url, destination)
                onResult(true)
            } catch (e: Exception) {
                Log.e("FileViewModel", "Operation failed", e)
                destination.delete()
                onResult(false)
            }
        }
    }

    fun getMimeTypeFromExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").trim()
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    fun deleteFile(fileId: Long) {
        if (fileId in _deletingFileIds.value) return
        _deletingFileIds.value = _deletingFileIds.value + fileId
        viewModelScope.launch {
            try {
                repo.deletefiles(fileId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FileViewModel", "Delete failed", e)
            } finally {
                _deletingFileIds.value = _deletingFileIds.value - fileId
            }
        }
    }
}
