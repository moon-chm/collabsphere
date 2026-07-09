package com.example.rohit_project_challlange.model.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.rohit_project_challlange.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM task WHERE workspaceId = :workspaceId")
    fun getTasksForWorkspace(workspaceId: Int): Flow<List<TaskEntity>>

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM task WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int)

    @Query("UPDATE task SET id = :newId WHERE id = :oldId")
    suspend fun updateTaskId(oldId: Int, newId: Int)

    @Query("""
        SELECT DISTINCT u.* FROM users u 
        LEFT JOIN workspace_members wm ON u.id = wm.userId 
        LEFT JOIN task t ON u.id = t.assignedToUserId OR u.id = t.createdByUserId
        WHERE wm.workspaceId = :workspaceId OR t.workspaceId = :workspaceId
    """)
    fun getWorkspaceMembers(workspaceId: Int): Flow<List<UserEntity>>
}