package io.github.jakub_m_arch.voicemator.history

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dictation history, stored only on this device. Saving can be turned off; existing entries
 * stay until the user clears them.
 */
class HistoryRepository private constructor(context: Context) {

    private val dao = HistoryDatabase.get(context).transcriptions()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableIsEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))

    val isEnabled: StateFlow<Boolean> = mutableIsEnabled.asStateFlow()
    val transcriptions: Flow<List<Transcription>> = dao.observeAll()
    val usage: Flow<List<UsageEntry>> = dao.observeUsage()

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        mutableIsEnabled.value = enabled
    }

    /** Saves a dictation if history is enabled. */
    suspend fun record(text: String, audioDurationMillis: Long, finishedAt: Long = System.currentTimeMillis()) {
        if (!isEnabled.value) return
        dao.insert(
            Transcription(
                createdAt = finishedAt,
                text = text,
                wordCount = countWords(text),
                audioDurationMillis = audioDurationMillis,
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.deleteAll()

    companion object {
        private const val PREFERENCES_NAME = "history"
        private const val KEY_ENABLED = "enabled"

        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
