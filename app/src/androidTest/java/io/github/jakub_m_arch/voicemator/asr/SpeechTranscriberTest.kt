package io.github.jakub_m_arch.voicemator.asr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-end check of the speech model on a device.
 *
 * Skipped unless the model is installed, either through the app or by pushing the files to
 * `/sdcard/Android/data/io.github.jakub_m_arch.voicemator/files/models/parakeet-ultra-int8/`.
 */
@RunWith(AndroidJUnit4::class)
class SpeechTranscriberTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun transcribesPolishSpeech() {
        val storage = ModelStorage(instrumentation.targetContext)
        assumeTrue("Speech model is not installed", storage.isInstalled())

        val samples = instrumentation.context.assets.open("fleurs-pl.wav").use(::readPcm16Wav)
        val text = SpeechTranscriber.load(storage).use { it.transcribe(samples, SpeechTranscriber.SAMPLE_RATE) }
        Log.i(TAG, "Transcription: $text")

        assertTrue("Unexpected transcription: $text", text.contains("satelity", ignoreCase = true))
    }

    @Test
    fun transcribesLongRecordingInChunks() = runBlocking {
        val storage = ModelStorage(instrumentation.targetContext)
        assumeTrue("Speech model is not installed", storage.isInstalled())

        // About 36 seconds: long enough to be split into chunks while "recording".
        val clip = instrumentation.context.assets.open("fleurs-pl.wav").use(::readPcm16Wav)
        val pause = FloatArray(SpeechTranscriber.SAMPLE_RATE * 4 / 5)
        val recording = List(REPEATS) { clip + pause }.reduce(FloatArray::plus)

        val transcriber = async(Dispatchers.Default) { SpeechTranscriber.load(storage) }
        val transcription = ChunkedTranscription(this, transcriber, SpeechTranscriber.SAMPLE_RATE, minSamples = 1)
        recording.toList().chunked(SpeechTranscriber.SAMPLE_RATE / 20).forEach {
            transcription.append(it.toFloatArray(), it.size)
        }
        val text = transcription.finish()
        transcriber.await().close()
        Log.i(TAG, "Chunked transcription: $text")

        val mentions = Regex("satelit", RegexOption.IGNORE_CASE).findAll(text).count()
        assertEquals("Unexpected transcription: $text", REPEATS, mentions)
    }

    /** Reads a 16-bit mono WAV file sampled at [SpeechTranscriber.SAMPLE_RATE]. */
    private fun readPcm16Wav(input: InputStream): FloatArray {
        val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(RIFF_HEADER_SIZE)
        while (buffer.remaining() >= CHUNK_HEADER_SIZE) {
            val id = ByteArray(4).also(buffer::get).decodeToString()
            val size = buffer.int
            when (id) {
                "fmt " -> {
                    val format = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                    assertEquals("channels", 1, format.getShort(2).toInt())
                    assertEquals("sample rate", SpeechTranscriber.SAMPLE_RATE, format.getInt(4))
                    assertEquals("bits per sample", 16, format.getShort(14).toInt())
                }
                "data" -> {
                    val pcm = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    return FloatArray(size / Short.SIZE_BYTES) { pcm[it] / PCM16_SCALE }
                }
            }
            buffer.position(buffer.position() + size + size % 2)
        }
        error("WAV file has no data chunk")
    }

    private companion object {
        const val TAG = "SpeechTranscriberTest"
        const val RIFF_HEADER_SIZE = 12
        const val CHUNK_HEADER_SIZE = 8
        const val PCM16_SCALE = 32768f
        const val REPEATS = 5
    }
}
