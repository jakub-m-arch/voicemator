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
import java.io.IOException

/** Records mono float PCM from the microphone into memory until [stop] is called. */
class AudioRecorder(private val sampleRate: Int) {

    @Volatile
    private var keepRecording = false
    private var recording: Deferred<FloatArray>? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope) {
        check(recording == null) { "Recording is already in progress" }
        val audioRecord = createAudioRecord()
        keepRecording = true
        recording = scope.async(Dispatchers.IO) { readUntilStopped(audioRecord) }
    }

    /** Stops recording and returns everything captured since [start]. */
    suspend fun stop(): FloatArray {
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

    private fun readUntilStopped(audioRecord: AudioRecord): FloatArray {
        val chunks = mutableListOf<FloatArray>()
        val buffer = FloatArray(sampleRate / CHUNKS_PER_SECOND)
        try {
            audioRecord.startRecording()
            while (keepRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) throw IOException("Microphone read failed with code $read")
                if (read > 0) chunks += buffer.copyOf(read)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        return concatenate(chunks)
    }

    private fun concatenate(chunks: List<FloatArray>): FloatArray {
        val result = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private companion object {
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        const val CHUNKS_PER_SECOND = 10
    }
}
