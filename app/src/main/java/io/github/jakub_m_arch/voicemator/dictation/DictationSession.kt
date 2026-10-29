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
import io.github.jakub_m_arch.voicemator.text.PostProcessing
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
 */
class DictationSession(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val recorder = AudioRecorder(SpeechTranscriber.SAMPLE_RATE)
    private var loadedTranscriber: SpeechTranscriber? = null

    // The model loads in parallel with recording, which hides most of its loading time.
    private val transcriber: Deferred<SpeechTranscriber> = scope.async(Dispatchers.Default) {
        SpeechTranscriber.load(ModelStorage(appContext)).also { loadedTranscriber = it }
    }
    private val transcription = ChunkedTranscription(
        scope = scope,
        transcriber = transcriber,
        sampleRate = SpeechTranscriber.SAMPLE_RATE,
        minSamples = MIN_SAMPLES,
        finalPass = TranscriptionSettings.get(appContext).finalPass.value,
    )

    /** Loudness of the microphone, from 0 to 1. */
    val level: StateFlow<Float> = recorder.level

    // The dictionary loads alongside the model; until it is ready the preview shows raw text.
    private var loadedPostProcessor: TextPostProcessor? = null
    private val postProcessor: Deferred<TextPostProcessor> = scope.async(Dispatchers.IO) {
        PostProcessing.load(appContext).also { loadedPostProcessor = it }
    }

    /** The text recognised so far, refreshed about once a second while recording. */
    val preview: StateFlow<String> = transcription.preview
        .map { text -> loadedPostProcessor?.process(text) ?: text }
        .stateIn(scope, SharingStarted.Eagerly, "")

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() = recorder.start(scope, transcription::append)

    /** Stops recording and returns the text, saved to the history; empty if nothing was said. */
    suspend fun finish(): String {
        recorder.stop()
        val text = postProcessor.await().process(transcription.finish())
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
        postProcessor.cancel()
        transcriber.cancel()
        loadedTranscriber?.close()
    }

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
