package io.github.jakub_m_arch.voicemator.history

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is kept after a dictation. */
enum class HistoryMode {
    /** The text and its statistics. */
    Full,

    /** Only word count, length and time, so statistics work but nothing can be read back. */
    StatisticsOnly,

    /** Nothing. */
    Off,
}

/**
 * Dictation history, stored only on this device. Changing the mode affects new dictations;
 * existing entries stay until the user removes their texts or clears the history.
 */
class HistoryRepository private constructor(context: Context) {

    private val dao = HistoryDatabase.get(context).transcriptions()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableMode = MutableStateFlow(loadMode())

    val mode: StateFlow<HistoryMode> = mutableMode.asStateFlow()

    /** Entries that still have their text, newest first. */
    val transcriptions: Flow<List<Transcription>> = dao.observeWithText()
    val usage: Flow<List<UsageEntry>> = dao.observeUsage()

    /** How many saved dictations still have their text. */
    val textCount: Flow<Int> = dao.observeTextCount()

    fun setMode(mode: HistoryMode) {
        preferences.edit { putString(KEY_MODE, mode.name) }
        mutableMode.value = mode
    }

    /** Saves a dictation as the current [mode] allows. */
    suspend fun record(text: String, audioDurationMillis: Long, finishedAt: Long = System.currentTimeMillis()) {
        val mode = mode.value
        if (mode == HistoryMode.Off) return
        dao.insert(
            Transcription(
                createdAt = finishedAt,
                text = if (mode == HistoryMode.Full) text else "",
                wordCount = countWords(text),
                audioDurationMillis = audioDurationMillis,
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Removes the texts of all saved dictations but keeps their statistics. */
    suspend fun removeTexts() = dao.clearTexts()

    /** Removes everything, statistics included. */
    suspend fun clear() = dao.deleteAll()

    private fun loadMode(): HistoryMode {
        preferences.getString(KEY_MODE, null)?.let { return HistoryMode.valueOf(it) }
        // Versions before 0.6 only had an on/off switch.
        return if (preferences.getBoolean(LEGACY_KEY_ENABLED, true)) HistoryMode.Full else HistoryMode.Off
    }

    companion object {
        private const val PREFERENCES_NAME = "history"
        private const val KEY_MODE = "mode"
        private const val LEGACY_KEY_ENABLED = "enabled"

        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
