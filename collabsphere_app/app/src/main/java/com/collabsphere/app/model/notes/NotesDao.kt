package com.collabsphere.app.model.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createNotes(notes: NotesEntity): Long

    @Query("SELECT * FROM notes WHERE workspaceId = :workspaceId ORDER BY isPinned DESC, id ASC")
    fun getallnotedbyuser(workspaceId: Int): Flow<List<NotesEntity>>

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :noteId")
    suspend fun updatePinned(noteId: Int, pinned: Boolean)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Int)

    @Query("UPDATE notes SET id = :newId WHERE id = :oldId")
    suspend fun updateNotesId(oldId: Int, newId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNotes(notes: List<NotesEntity>)

    @Update
    suspend fun updatenotes(notes: NotesEntity)

    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY id DESC")
    fun getNotesByUser(userId: Int): Flow<List<NotesEntity>>

    @Upsert
    suspend fun upsertAll(notes: List<NotesEntity>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Transaction
    suspend fun applyDelta(upserts: List<NotesEntity>, deletes: List<Int>) {
        if (deletes.isNotEmpty()) deleteByIds(deletes)
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }
}