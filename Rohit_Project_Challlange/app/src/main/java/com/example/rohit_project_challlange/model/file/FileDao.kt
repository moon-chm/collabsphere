package com.example.rohit_project_challlange.model.file

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Query("SELECT * FROM local_files WHERE workspaceId = :workspaceId")
    fun getallfiles(workspaceId: Int): Flow<List<FileEntity>>

    @Query("DELETE FROM local_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)
}