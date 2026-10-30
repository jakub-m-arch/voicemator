package io.github.jakub_m_arch.voicemator.files

import android.content.Context
import android.net.Uri
import io.github.jakub_m_arch.voicemator.asr.RecordingSegmenter
import io.github.jakub_m_arch.voicemator.asr.SpeechTranscriber
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.text.DictationRules
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/** A stretch of a recording and what was said in it. */
data class TranscriptSegment(val startMillis: Long, val endMillis: Long, val text: String)

/** The text of a whole file, in segments with their times. */
data class FileTranscript(val segments: List<TranscriptSegment>, val durationMillis: Long) {
    val text: String get() = segments.joinToString(" ") { it.text }
}

/**
 * Transcribes an audio file of any length: it is decoded a few seconds at a time and cut into
 * chunks by the same [RecordingSegmenter] as dictation, and each chunk is transcribed as soon as
 * it is ready. Run it off the main thread.
 */
class FileTranscriber(private val context: Context) {

    /** How far transcription has got: [fraction] of the file, when its length is known, and the text so far. */
    data class Progress(val fraction: Float?, val text: String)

    suspend fun transcribe(uri: Uri, onProgress: (Progress) -> Unit): FileTranscript {
        val job = coroutineContext.job
        val postProcessor = DictationRules.load(context).postProcessor(profile = null)
        val segmenter = RecordingSegmenter(SpeechTranscriber.SAMPLE_RATE)
        val segments = mutableListOf<TranscriptSegment>()
        var transcribedSamples = 0L
        var decodedFraction: Float? = null

        SpeechTranscriber.load(ModelStorage(context)).use { transcriber ->
            fun transcribe(chunk: FloatArray) {
                job.ensureActive()
                val text = postProcessor.process(transcriber.transcribe(chunk, SpeechTranscriber.SAMPLE_RATE))
                val start = transcribedSamples
                transcribedSamples += chunk.size
                if (text.isNotBlank()) segments += TranscriptSegment(millis(start), millis(transcribedSamples), text)
                onProgress(Progress(decodedFraction, segments.joinToString(" ") { it.text }))
            }

            AudioFileDecoder(context).decode(
                uri = uri,
                targetRate = SpeechTranscriber.SAMPLE_RATE,
                onProgress = { decodedFraction = it },
                onAudio = { audio -> segmenter.append(audio).forEach(::transcribe) },
            )
            segmenter.flush()?.let(::transcribe)
        }
        coroutineContext.ensureActive()
        return FileTranscript(segments, millis(transcribedSamples))
    }

    private fun millis(samples: Long) = samples * MILLIS_PER_SECOND / SpeechTranscriber.SAMPLE_RATE

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
