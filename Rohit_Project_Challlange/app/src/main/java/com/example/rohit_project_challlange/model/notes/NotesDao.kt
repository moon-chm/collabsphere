package com.example.rohit_project_challlange.model.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createNotes(notes: NotesEntity): Long

    @Query("SELECT * FROM notes WHERE workspaceId = :workspaceId")
    fun getallnotedbyuser(workspaceId: Int): Flow<List<NotesEntity>>

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Int)

    @Query("UPDATE notes SET id = :newId WHERE id = :oldId")
    suspend fun updateNotesId(oldId: Int, newId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNotes(notes: List<NotesEntity>)

    @Update
    suspend fun updatenotes(notes: NotesEntity)
}