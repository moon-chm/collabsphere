package com.collabsphere.app.model.file

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Upsert
    suspend fun insertAllFiles(files: List<FileEntity>)

    @Query("SELECT * FROM local_files WHERE workspaceId = :workspaceId")
    fun getallfiles(workspaceId: Int): Flow<List<FileEntity>>

    @Query("DELETE FROM local_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)

    @Query("DELETE FROM local_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun applyDelta(upserts: List<FileEntity>, deletes: List<Long>) {
        if (deletes.isNotEmpty()) deleteByIds(deletes)
        if (upserts.isNotEmpty()) insertAllFiles(upserts)
    }
}