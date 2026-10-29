package io.github.jakub_m_arch.voicemator.text

import android.content.Context
import io.github.jakub_m_arch.voicemator.dictation.TranscriptionSettings
import io.github.jakub_m_arch.voicemator.history.HistoryDatabase

/** Builds the [TextPostProcessor] for the user's current dictionary and settings. */
object PostProcessing {
    suspend fun load(context: Context): TextPostProcessor {
        val entries = HistoryDatabase.get(context).dictionary().getAll()
        return TextPostProcessor(
            replacements = entries.map(DictionaryEntry::toReplacement),
            removeFillers = TranscriptionSettings.get(context).removeFillers.value,
        )
    }
}
