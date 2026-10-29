package io.github.jakub_m_arch.voicemator.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transcription::class], version = 1)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun transcriptions(): TranscriptionDao

    companion object {
        private const val NAME = "history.db"

        @Volatile
        private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
