package com.example.rohit_project_challlange.viewmodel.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo

class WorkspaceViewModelFactory(
    private val repo: WorkspaceRepo,
    private val loggedInUserId: Int
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(repo, loggedInUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}