package io.github.jakub_m_arch.voicemator.dictation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DictationState { Idle, Recording, Transcribing }

/** Process-wide dictation state, written by [DictationService] and observed by the UI and tile. */
object DictationStatus {
    private val mutableState = MutableStateFlow(DictationState.Idle)

    val state: StateFlow<DictationState> = mutableState.asStateFlow()

    internal fun update(state: DictationState) {
        mutableState.value = state
    }
}
