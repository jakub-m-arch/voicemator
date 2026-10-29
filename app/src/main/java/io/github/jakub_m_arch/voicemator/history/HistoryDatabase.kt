package io.github.jakub_m_arch.voicemator.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.jakub_m_arch.voicemator.text.DictionaryDao
import io.github.jakub_m_arch.voicemator.text.DictionaryEntry

/** The app's database: dictation history and the user's dictionary. */
@Database(
    entities = [Transcription::class, DictionaryEntry::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun transcriptions(): TranscriptionDao

    abstract fun dictionary(): DictionaryDao

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
