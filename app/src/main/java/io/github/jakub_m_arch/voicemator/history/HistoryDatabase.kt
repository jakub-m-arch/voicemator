package io.github.jakub_m_arch.voicemator.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.jakub_m_arch.voicemator.profiles.Profile
import io.github.jakub_m_arch.voicemator.profiles.ProfileApp
import io.github.jakub_m_arch.voicemator.profiles.ProfileDao
import io.github.jakub_m_arch.voicemator.text.DictionaryDao
import io.github.jakub_m_arch.voicemator.text.DictionaryEntry

/** The app's database: dictation history, the user's dictionary and app profiles. */
@Database(
    entities = [Transcription::class, DictionaryEntry::class, Profile::class, ProfileApp::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun transcriptions(): TranscriptionDao

    abstract fun dictionary(): DictionaryDao

    abstract fun profiles(): ProfileDao

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
