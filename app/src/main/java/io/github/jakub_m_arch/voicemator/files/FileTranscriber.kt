package io.github.jakub_m_arch.voicemator.files

import android.content.Context
import android.net.Uri
import io.github.jakub_m_arch.voicemator.asr.RecordingSegmenter
import io.github.jakub_m_arch.voicemator.asr.SpeechTranscriber
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.text.PostProcessing
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Transcribes an audio file in chunks, reporting progress. Run it off the main thread. */
class FileTranscriber(private val context: Context) {

    sealed interface Progress {
        data class Decoding(val fraction: Float) : Progress
        data class Transcribing(val fraction: Float) : Progress
    }

    suspend fun transcribe(uri: Uri, onProgress: (Progress) -> Unit): String {
        val audio = AudioFileDecoder(context).decode(uri, SpeechTranscriber.SAMPLE_RATE, MAX_SECONDS) {
            onProgress(Progress.Decoding(it))
        }
        coroutineContext.ensureActive()

        val segmenter = RecordingSegmenter(SpeechTranscriber.SAMPLE_RATE)
        val chunks = segmenter.append(audio) + listOfNotNull(segmenter.flush())
        val postProcessor = PostProcessing.load(context)
        onProgress(Progress.Transcribing(0f))
        val texts = SpeechTranscriber.load(ModelStorage(context)).use { transcriber ->
            chunks.mapIndexed { index, chunk ->
                coroutineContext.ensureActive()
                transcriber.transcribe(chunk, SpeechTranscriber.SAMPLE_RATE)
                    .also { onProgress(Progress.Transcribing((index + 1f) / chunks.size)) }
            }
        }
        return postProcessor.process(texts.filter { it.isNotBlank() }.joinToString(" "))
    }

    companion object {
        /** Longer files would need too much memory; voice messages are far shorter. */
        const val MAX_SECONDS = 20 * 60
    }
}
