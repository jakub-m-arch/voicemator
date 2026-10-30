package io.github.jakub_m_arch.voicemator.asr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transcribes a recording while it is still being made.
 *
 * Audio passed to [append] is split by [RecordingSegmenter]; each finished chunk is transcribed
 * in the background, one at a time. In between, the chunk still being recorded is transcribed
 * about once a second to keep [preview] current. When dictation stops, [finish] only has to
 * transcribe the last chunk, so the text is ready almost immediately even after a long recording.
 *
 * With a final pass, [finish] instead transcribes the whole recording again as one utterance,
 * which avoids seams between chunks at the cost of a few seconds' wait; recordings longer than
 * [MAX_FINAL_PASS_SECONDS] still use the chunks, as one pass over them would take too long and
 * too much memory on a phone. Whether to make one is decided only when dictation stops, because
 * an app profile may ask for it, so short recordings are always kept (at most about 11 MB).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChunkedTranscription(
    scope: CoroutineScope,
    private val transcriber: Deferred<SpeechTranscriber>,
    private val sampleRate: Int,
    private val minSamples: Int,
    private val segmenter: RecordingSegmenter = RecordingSegmenter(sampleRate),
) {
    // One thread for every use of the model, so chunks and previews never decode at once.
    private val modelDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val chunks = Channel<FloatArray>(Channel.UNLIMITED)
    private val segmenterLock = Any()
    private val finishedTexts = mutableListOf<String>()
    private val mutablePreview = MutableStateFlow("")

    /** The whole recording, kept for a final pass while it is short enough. */
    private val recording = mutableListOf<FloatArray>()
    private val maxFinalPassSamples = MAX_FINAL_PASS_SECONDS.toLong() * sampleRate

    @Volatile
    private var recordedSamples = 0L

    /** Length of the audio received so far. */
    val recordedMillis: Long get() = recordedSamples * MILLIS_PER_SECOND / sampleRate

    /** Best guess at the text so far, refreshed while recording; empty until something is heard. */
    val preview: StateFlow<String> = mutablePreview.asStateFlow()

    private val transcripts: Deferred<List<String>> = scope.async(modelDispatcher) {
        for (chunk in chunks) {
            finishedTexts += transcriber.await().transcribe(chunk, sampleRate)
            mutablePreview.value = joined(finishedTexts)
        }
        finishedTexts.toList()
    }

    private val previews = scope.launch(modelDispatcher) {
        var previewedSamples = 0
        while (isActive) {
            delay(PREVIEW_INTERVAL_MILLIS)
            val pending = synchronized(segmenterLock) { segmenter.pending() }
            // Skip while less than half a second is pending, or when nothing new has arrived.
            if (pending.size < sampleRate / 2 || pending.size == previewedSamples) continue
            previewedSamples = pending.size
            val partial = transcriber.await().transcribe(pending, sampleRate)
            mutablePreview.value = joined(finishedTexts + partial)
        }
    }

    /** Adds recorded audio; called from the recording thread. */
    fun append(samples: FloatArray, count: Int) {
        recordedSamples += count
        val ready = synchronized(segmenterLock) {
            when {
                isShortEnoughForFinalPass() -> recording += samples.copyOf(count)
                recording.isNotEmpty() -> recording.clear() // Too long now; the chunks will do.
            }
            segmenter.append(samples, count)
        }
        ready.forEach { chunks.trySend(it) }
    }

    /**
     * Transcribes the remaining audio and returns the whole text, with one more pass over the whole
     * recording if [finalPass] is set and it is short enough. Recordings shorter than [minSamples]
     * are treated as accidental and return an empty string.
     */
    suspend fun finish(finalPass: Boolean = false): String {
        previews.cancel()
        if (recordedSamples < minSamples) {
            cancel()
            return ""
        }
        if (finalPass && isShortEnoughForFinalPass()) {
            chunks.cancel()
            transcripts.cancel()
            val audio = synchronized(segmenterLock) { concatenate(recording) }
            return withContext(modelDispatcher) { transcriber.await().transcribe(audio, sampleRate) }
        }
        synchronized(segmenterLock) { segmenter.flush() }?.let { chunks.trySend(it) }
        chunks.close()
        return joined(transcripts.await())
    }

    fun cancel() {
        previews.cancel()
        chunks.cancel()
        transcripts.cancel()
    }

    private fun isShortEnoughForFinalPass() = recordedSamples <= maxFinalPassSamples

    private fun joined(texts: List<String>) = texts.filter { it.isNotBlank() }.joinToString(" ")

    private fun concatenate(parts: List<FloatArray>): FloatArray {
        val result = FloatArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }

    companion object {
        const val MAX_FINAL_PASS_SECONDS = 180

        private const val MILLIS_PER_SECOND = 1000L
        private const val PREVIEW_INTERVAL_MILLIS = 800L
    }
}
