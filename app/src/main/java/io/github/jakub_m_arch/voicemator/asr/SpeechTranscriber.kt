package io.github.jakub_m_arch.voicemator.asr

import io.github.jakub_m_arch.voicemator.model.ModelFile
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.model.ParakeetModel
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.Closeable

/** Offline speech-to-text backed by sherpa-onnx. Loading the model takes a few seconds. */
class SpeechTranscriber private constructor(private val recognizer: OfflineRecognizer) : Closeable {

    /** Transcribes mono PCM [samples] in the range [-1, 1] recorded at [sampleRate] Hz. */
    fun transcribe(samples: FloatArray, sampleRate: Int): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    companion object {
        const val SAMPLE_RATE = 16_000

        private const val NUM_THREADS = 4

        fun load(storage: ModelStorage): SpeechTranscriber {
            fun path(file: ModelFile) = storage.fileFor(file).absolutePath

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = path(ParakeetModel.encoder),
                        decoder = path(ParakeetModel.decoder),
                        joiner = path(ParakeetModel.joiner),
                    ),
                    tokens = path(ParakeetModel.tokens),
                    modelType = "nemo_transducer",
                    numThreads = NUM_THREADS,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            return SpeechTranscriber(OfflineRecognizer(config = config))
        }
    }
}
