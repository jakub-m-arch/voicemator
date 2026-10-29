package io.github.jakub_m_arch.voicemator.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A finished dictation, as kept in the history. */
@Entity(tableName = "transcriptions", indices = [Index("created_at")])
data class Transcription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** When dictation finished, in milliseconds since the epoch. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val text: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "audio_duration_ms") val audioDurationMillis: Long,
)

/** The part of a [Transcription] that statistics need, without its text. */
data class UsageEntry(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "audio_duration_ms") val audioDurationMillis: Long,
)
