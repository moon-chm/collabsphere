package com.example.rohit_project_challlange.viewmodel.file

import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.model.file.FileEntity
import com.example.rohit_project_challlange.model.file.FileRepo
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

    val getallfile: StateFlow<List<FileEntity>> =
        repo.getfiles(loggedWorkspaceId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uploadingStatus = MutableStateFlow<Boolean>(false)
    val uploadingStatus = _uploadingStatus.asStateFlow()

    fun uploadPhysicalFile(selectedFile: File, customMimeType: String? = null) {
        if (!selectedFile.exists() || selectedFile.length() == 0L) return

        viewModelScope.launch {
            try {
                _uploadingStatus.value = true

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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uploadingStatus.value = false
            }
        }
    }

    fun downloadFile(url: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = repo.downloadFileFromServer(url)
                onResult(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
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
        viewModelScope.launch {
            repo.deletefiles(fileId)
        }
    }
}