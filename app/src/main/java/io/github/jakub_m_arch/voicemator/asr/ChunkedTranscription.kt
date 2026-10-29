package io.github.jakub_m_arch.voicemator.asr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel

/**
 * Transcribes a recording while it is still being made.
 *
 * Audio passed to [append] is split by [RecordingSegmenter]; each finished chunk is transcribed
 * in the background, one at a time. When dictation stops, [finish] only has to transcribe the
 * last chunk, so the text is ready almost immediately even after a long recording.
 */
class ChunkedTranscription(
    scope: CoroutineScope,
    private val transcriber: Deferred<SpeechTranscriber>,
    private val sampleRate: Int,
    private val minSamples: Int,
    private val segmenter: RecordingSegmenter = RecordingSegmenter(sampleRate),
) {
    private val chunks = Channel<FloatArray>(Channel.UNLIMITED)

    @Volatile
    private var recordedSamples = 0L

    private val transcripts: Deferred<List<String>> = scope.async(Dispatchers.Default) {
        val results = mutableListOf<String>()
        for (chunk in chunks) {
            results += transcriber.await().transcribe(chunk, sampleRate)
        }
        results
    }

    /** Adds recorded audio; called from the recording thread. */
    fun append(samples: FloatArray, count: Int) {
        recordedSamples += count
        segmenter.append(samples, count).forEach { chunks.trySend(it) }
    }

    /**
     * Transcribes the remaining audio and returns the whole text. Recordings shorter than
     * [minSamples] are treated as accidental and return an empty string.
     */
    suspend fun finish(): String {
        if (recordedSamples < minSamples) {
            cancel()
            return ""
        }
        segmenter.flush()?.let { chunks.trySend(it) }
        chunks.close()
        return transcripts.await().filter { it.isNotBlank() }.joinToString(" ")
    }

    fun cancel() {
        chunks.cancel()
        transcripts.cancel()
    }
}
