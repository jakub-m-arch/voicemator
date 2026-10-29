package io.github.jakub_m_arch.voicemator.ime

import android.annotation.SuppressLint
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import io.github.jakub_m_arch.voicemator.MainActivity
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationSession
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A voice input method: keyboards with a mic key that hands over to the system's voice input
 * (such as FUTO Keyboard with its built-in voice input turned off) open this panel. Dictation
 * starts right away and the text appears in the field as composing text while the user speaks;
 * stopping commits it and returns to the keyboard.
 *
 * It records in its own process rather than through `DictationService`: an input method may use
 * the microphone while it is shown, but may not start a microphone foreground service.
 */
class VoiceInputMethodService : InputMethodService() {

    private var panel: VoiceInputPanel? = null
    private var scope: CoroutineScope? = null
    private var session: DictationSession? = null

    /** Space to put before the dictated text, decided before any of it is shown. */
    private var separator = ""

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onDestroy() {
        discard()
        current = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View =
        VoiceInputPanel(
            context = this,
            onStop = ::stop,
            onCancel = ::cancel,
            onOpenApp = ::openApp,
        ).also { panel = it }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (!restarting) start()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Leaving the panel before the text is in, for example by switching keyboards, discards it.
        discard()
        super.onFinishInputView(finishingInput)
    }

    @SuppressLint("MissingPermission") // Checked by DictationRequirements.
    private fun start() {
        if (session != null) return
        if (!DictationRequirements.areMet(this)) {
            panel?.showSetupNeeded()
            return
        }
        // Another dictation, for example from the tile, is still running.
        if (DictationStatus.state.value != DictationState.Idle) {
            returnToKeyboard()
            return
        }
        val scope = MainScope()
        val session = DictationSession(this, scope)
        try {
            session.start()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start recording", e)
            session.close()
            scope.cancel()
            returnToKeyboard()
            return
        }
        this.scope = scope
        this.session = session
        separator = separatorBeforeCursor()
        updateState(DictationState.Recording)
        scope.launch { session.level.collect { panel?.pushLevel(it) } }
        scope.launch {
            session.preview.collect { preview ->
                if (preview.isNotEmpty()) currentInputConnection?.setComposingText(separator + preview, 1)
            }
        }
    }

    private fun stop() {
        val session = session ?: return
        val scope = scope ?: return
        if (DictationStatus.state.value != DictationState.Recording) return
        updateState(DictationState.Transcribing)
        scope.launch {
            val text = try {
                session.finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dictation failed", e)
                ""
            }
            end()
            if (text.isNotEmpty()) {
                // Replaces the preview with the final text.
                currentInputConnection?.commitText(separator + text, 1)
            } else {
                clearPreview()
            }
            returnToKeyboard()
        }
    }

    private fun cancel() {
        discard()
        returnToKeyboard()
    }

    /** Stops an unfinished dictation without inserting anything. */
    private fun discard() {
        val session = session ?: return
        val scope = scope ?: return
        this.session = null
        clearPreview()
        scope.launch {
            session.cancel()
            session.close()
            scope.cancel()
        }
        updateState(DictationState.Idle)
    }

    /** Releases a finished session. */
    private fun end() {
        session?.close()
        session = null
        scope?.cancel()
        scope = null
        updateState(DictationState.Idle)
    }

    /** Types [text] at the cursor, as a finished dictation would. */
    internal fun insertText(text: String) {
        currentInputConnection?.commitText(separatorBeforeCursor() + text, 1)
    }

    /** A space when the text would otherwise touch the word before the cursor. */
    private fun separatorBeforeCursor(): String {
        val previous = currentInputConnection?.getTextBeforeCursor(1, 0)
        return if (!previous.isNullOrEmpty() && !previous.last().isWhitespace()) " " else ""
    }

    private fun clearPreview() {
        val connection = currentInputConnection ?: return
        connection.setComposingText("", 1)
        connection.finishComposingText()
    }

    private fun updateState(state: DictationState) {
        DictationStatus.publish(this, state)
        panel?.showState(state)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        returnToKeyboard()
    }

    private fun returnToKeyboard() {
        if (!switchToPreviousInputMethod()) requestHideSelf(0)
    }

    companion object {
        private const val TAG = "VoiceInputMethod"

        /** The running instance, for debug tooling that stands in for a real dictation. */
        @Volatile
        internal var current: VoiceInputMethodService? = null
            private set
    }
}
