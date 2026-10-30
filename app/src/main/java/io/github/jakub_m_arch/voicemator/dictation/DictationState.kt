package io.github.jakub_m_arch.voicemator.dictation

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import io.github.jakub_m_arch.voicemator.widget.DictationWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DictationState { Idle, Recording, Transcribing }

/**
 * Process-wide dictation state, written by [DictationService] and the voice input method, and
 * observed by the UI, the tile, the widget and the keyboard button.
 */
object DictationStatus {
    private val mutableState = MutableStateFlow(DictationState.Idle)

    val state: StateFlow<DictationState> = mutableState.asStateFlow()

    /** Publishes [state] and redraws the tile and the widget, which do not observe it on their own. */
    internal fun publish(context: Context, state: DictationState) {
        mutableState.value = state
        TileService.requestListeningState(context, ComponentName(context, DictationTileService::class.java))
        DictationWidget.update(context, state)
    }
}
