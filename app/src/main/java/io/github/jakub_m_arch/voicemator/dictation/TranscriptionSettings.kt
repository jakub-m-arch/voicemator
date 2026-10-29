package io.github.jakub_m_arch.voicemator.dictation

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How dictations are transcribed. */
class TranscriptionSettings private constructor(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableFinalPass = MutableStateFlow(preferences.getBoolean(KEY_FINAL_PASS, false))

    /** Whether the whole recording is transcribed again as one piece after dictation stops. */
    val finalPass: StateFlow<Boolean> = mutableFinalPass.asStateFlow()

    fun setFinalPass(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FINAL_PASS, enabled) }
        mutableFinalPass.value = enabled
    }

    companion object {
        private const val PREFERENCES_NAME = "transcription"
        private const val KEY_FINAL_PASS = "final_pass"

        @Volatile
        private var instance: TranscriptionSettings? = null

        fun get(context: Context): TranscriptionSettings =
            instance ?: synchronized(this) {
                instance ?: TranscriptionSettings(context.applicationContext).also { instance = it }
            }
    }
}
