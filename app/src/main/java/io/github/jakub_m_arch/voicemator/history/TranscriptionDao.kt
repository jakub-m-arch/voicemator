package io.github.jakub_m_arch.voicemator.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Transcription>>

    @Query("SELECT created_at, word_count, audio_duration_ms FROM transcriptions")
    fun observeUsage(): Flow<List<UsageEntry>>

    @Insert
    suspend fun insert(transcription: Transcription)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
}
