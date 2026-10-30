package io.github.jakub_m_arch.voicemator.dictation

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import io.github.jakub_m_arch.voicemator.asr.ChunkedTranscription
import io.github.jakub_m_arch.voicemator.asr.SpeechTranscriber
import io.github.jakub_m_arch.voicemator.audio.AudioRecorder
import io.github.jakub_m_arch.voicemator.history.HistoryRepository
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.text.DictationRules
import io.github.jakub_m_arch.voicemator.text.TextPostProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * One dictation: records, transcribes while recording, and saves the result to the history.
 *
 * Used by [DictationService] and by the voice input method, which has to record itself because
 * Android does not let an input method start a microphone foreground service.
 *
 * [targetApp] names the app the text is for, when known, so its profile can shape the text. It
 * is asked when recording starts, for the preview, and again when it stops, since that is where
 * the text goes.
 */
class DictationSession(
    context: Context,
    private val scope: CoroutineScope,
    private val targetApp: () -> String? = { null },
) {

    private val appContext = context.applicationContext
    private val recorder = AudioRecorder(SpeechTranscriber.SAMPLE_RATE)
    private var loadedTranscriber: SpeechTranscriber? = null
    private var appAtStart: String? = null

    // The model loads in parallel with recording, which hides most of its loading time.
    private val transcriber: Deferred<SpeechTranscriber> = scope.async(Dispatchers.Default) {
        SpeechTranscriber.load(ModelStorage(appContext)).also { loadedTranscriber = it }
    }
    private val transcription = ChunkedTranscription(
        scope = scope,
        transcriber = transcriber,
        sampleRate = SpeechTranscriber.SAMPLE_RATE,
        minSamples = MIN_SAMPLES,
    )

    /** Loudness of the microphone, from 0 to 1. */
    val level: StateFlow<Float> = recorder.level

    // The dictionary and profiles load alongside the model; until then the preview shows raw text.
    private var loadedRules: DictationRules? = null
    private val rules: Deferred<DictationRules> = scope.async(Dispatchers.IO) {
        DictationRules.load(appContext).also { loadedRules = it }
    }
    private var previewProcessor: TextPostProcessor? = null

    /** The text recognised so far, refreshed about once a second while recording. */
    val preview: StateFlow<String> = transcription.preview
        .map { text -> previewProcessor()?.process(text) ?: text }
        .stateIn(scope, SharingStarted.Eagerly, "")

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        appAtStart = targetApp()
        recorder.start(scope, transcription::append)
    }

    /** Stops recording and returns the text, saved to the history; empty if nothing was said. */
    suspend fun finish(): String {
        recorder.stop()
        val rules = rules.await()
        val profile = rules.profileFor(targetApp() ?: appAtStart)
        val text = rules.postProcessor(profile).process(transcription.finish(rules.finalPass(profile)))
        if (text.isNotEmpty()) saveToHistory(text, transcription.recordedMillis)
        return text
    }

    /** Stops recording and discards it. */
    suspend fun cancel() {
        runCatching { recorder.stop() }
        transcription.cancel()
    }

    /** Frees the speech model. Call once the session is over. */
    fun close() {
        rules.cancel()
        transcriber.cancel()
        loadedTranscriber?.close()
    }

    private fun previewProcessor(): TextPostProcessor? =
        previewProcessor ?: loadedRules
            ?.let { it.postProcessor(it.profileFor(appAtStart)) }
            ?.also { previewProcessor = it }

    private suspend fun saveToHistory(text: String, audioDurationMillis: Long) {
        try {
            val history = HistoryRepository.get(appContext)
            withContext(Dispatchers.IO) { history.record(text, audioDurationMillis) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The text is delivered anyway; a history failure must not lose it.
            Log.e(TAG, "Saving to history failed", e)
        }
    }

    private companion object {
        const val TAG = "DictationSession"

        /** Recordings shorter than this are treated as accidental taps. */
        const val MIN_SAMPLES = SpeechTranscriber.SAMPLE_RATE / 4
    }
}
