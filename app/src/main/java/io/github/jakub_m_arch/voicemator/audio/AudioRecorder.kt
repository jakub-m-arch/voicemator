package io.github.jakub_m_arch.voicemator.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import kotlin.math.log10
import kotlin.math.sqrt

/** Streams mono float PCM from the microphone until [stop] is called. */
class AudioRecorder(private val sampleRate: Int) {

    @Volatile
    private var keepRecording = false
    private var recording: Deferred<Unit>? = null
    private val mutableLevel = MutableStateFlow(0f)

    /** Loudness of the latest audio chunk, from 0 (silence) to 1 (loud speech). */
    val level: StateFlow<Float> = mutableLevel.asStateFlow()

    /**
     * Starts recording. [onAudio] receives each chunk of samples and how many of them are valid,
     * on a background thread; the array is reused, so copy what you keep.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope, onAudio: (samples: FloatArray, count: Int) -> Unit) {
        check(recording == null) { "Recording is already in progress" }
        val audioRecord = createAudioRecord()
        keepRecording = true
        recording = scope.async(Dispatchers.IO) { readUntilStopped(audioRecord, onAudio) }
    }

    /** Stops recording once the last chunk has been delivered to `onAudio`. */
    suspend fun stop() {
        val current = checkNotNull(recording) { "Recording was not started" }
        keepRecording = false
        recording = null
        return current.await()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, ENCODING)
        check(minBufferSize > 0) { "Unsupported audio configuration" }
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            CHANNEL_CONFIG,
            ENCODING,
            maxOf(minBufferSize, sampleRate * Float.SIZE_BYTES / 2),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IOException("Microphone is unavailable")
        }
        return audioRecord
    }

    private fun readUntilStopped(audioRecord: AudioRecord, onAudio: (FloatArray, Int) -> Unit) {
        val buffer = FloatArray(sampleRate / CHUNKS_PER_SECOND)
        try {
            audioRecord.startRecording()
            while (keepRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) throw IOException("Microphone read failed with code $read")
                if (read > 0) {
                    onAudio(buffer, read)
                    mutableLevel.value = loudness(buffer, read)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
            mutableLevel.value = 0f
        }
    }

    /** Maps the RMS level of [count] samples from [MIN_DECIBELS]..[MAX_DECIBELS] to 0..1. */
    private fun loudness(samples: FloatArray, count: Int): Float {
        var sumOfSquares = 0.0
        for (i in 0 until count) sumOfSquares += samples[i] * samples[i]
        val rms = sqrt(sumOfSquares / count)
        if (rms <= 0.0) return 0f
        val decibels = 20 * log10(rms)
        return ((decibels - MIN_DECIBELS) / (MAX_DECIBELS - MIN_DECIBELS)).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        const val CHUNKS_PER_SECOND = 20
        const val MIN_DECIBELS = -50.0
        const val MAX_DECIBELS = -10.0
    }
}
