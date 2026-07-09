package com.example.rohit_project_challlange.viewmodel.dm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.dm.DmEntity
import com.example.rohit_project_challlange.model.dm.DmRepo
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import com.example.rohit_project_challlange.remote.dm.DmWebSocketService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DmViewModel(
    private val repo: DmRepo,
    private val workspaceRepo: WorkspaceRepo,
    private val notificationHelper: NotificationHelper,
    private val context: Context
) : ViewModel() {

    private val _workspaceMembers = MutableStateFlow<List<UserEntity>>(emptyList())
    val workspaceMembers: StateFlow<List<UserEntity>> = _workspaceMembers.asStateFlow()

    private val _messages = MutableStateFlow<List<DmEntity>>(emptyList())
    val messages: StateFlow<List<DmEntity>> = _messages.asStateFlow()

    private var historyCollectionJob: Job? = null
    private var memberCollectionJob: Job? = null
    private var activeChatPartnerId: Int? = null
    private var currentWorkspaceId: Int? = null
    private var currentUserId: Int? = null

    fun initWebSocketConnection(baseUrl: String, userId: Long) {
        currentUserId = userId.toInt()

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("BASE_URL", baseUrl)
            putExtra("USER_ID", userId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun loadWorkspaceMembers(workspaceId: Int) {
        memberCollectionJob?.cancel()
        memberCollectionJob = viewModelScope.launch {
            try {
                workspaceRepo.syncWorkspaceMembers(workspaceId)
            } catch (_: Exception) {}

            workspaceRepo.getWorkspaceMembersFlow(workspaceId)
                .catch { e ->
                    Log.e("DM_VM", "Failed reading members stream from room storage", e)
                    _workspaceMembers.value = emptyList()
                }
                .collect { members ->
                    _workspaceMembers.value = members
                }
        }
    }

    fun loadChatHistory(workspaceId: Int, userId: Int, chatPartnerId: Int, baseUrl: String) {
        currentWorkspaceId = workspaceId
        currentUserId = userId
        activeChatPartnerId = chatPartnerId

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("UPDATE_PARTNER_ID", chatPartnerId)
        }
        context.startService(intent)

        historyCollectionJob?.cancel()
        historyCollectionJob = viewModelScope.launch {
            repo.getDmHistory(workspaceId, userId, chatPartnerId)
                .catch { e -> Log.e("DM_VM", "History collection error", e) }
                .collect { history ->
                    _messages.value = history
                }
        }
    }

    fun closeChatSessionUi() {
        historyCollectionJob?.cancel()
        historyCollectionJob = null
        activeChatPartnerId = null

        val intent = Intent(context, DmWebSocketService::class.java).apply {
            putExtra("UPDATE_PARTNER_ID", -1)
        }
        context.startService(intent)
    }

    fun shutdownWebSocketEntirely() {
        closeChatSessionUi()
        memberCollectionJob?.cancel()
        memberCollectionJob = null

        val intent = Intent(context, DmWebSocketService::class.java)
        context.stopService(intent)
    }

    fun sendMessage(id: Int, workspaceId: Int, senderId: Int, receiverId: Int, content: String) {
        viewModelScope.launch {
            try {
                repo.sendRealtimeDm(id, workspaceId, senderId, receiverId, content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(dmId: Int, workspaceId: Int) {
        viewModelScope.launch {
            repo.deleteDm(dmId, workspaceId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentWorkspaceId = null
        currentUserId = null
        _messages.value = emptyList()
        closeChatSessionUi()
    }
}