package io.github.jakub_m_arch.voicemator.files

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes the audio track of a file (Ogg Opus voice messages, M4A, MP3, WAV, AMR and whatever else
 * the device can decode) into mono float samples at a target rate, a few seconds at a time, so
 * even hours of audio never have to fit in memory at once.
 */
class AudioFileDecoder(private val context: Context) {

    /**
     * Decodes [uri], passing audio at [targetRate] to [onAudio] block by block, in order.
     * [onProgress] gets a value from 0 to 1 when the duration is known. Throws [IOException] if
     * there is no audio track the device can decode.
     */
    fun decode(uri: Uri, targetRate: Int, onProgress: (Float) -> Unit = {}, onAudio: (FloatArray) -> Unit) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationMicros = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            decodeTrack(extractor, format, durationMicros, MonoBlocks(targetRate, onAudio), onProgress)
        } finally {
            extractor.release()
        }
    }

    private fun decodeTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        durationMicros: Long,
        output: MonoBlocks,
        onProgress: (Float) -> Unit,
    ) {
        val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
        output.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_MICROS)
                    if (inputIndex >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(inputIndex))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            if (durationMicros > 0) onProgress((extractor.sampleTime.toFloat() / durationMicros).coerceIn(0f, 1f))
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        output.sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    else -> if (outputIndex >= 0) {
                        val buffer = checkNotNull(codec.getOutputBuffer(outputIndex)).order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        output.appendMono(buffer, encoding, channels)
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.release()
        }
        output.flush()
        onProgress(1f)
    }

    /**
     * Collects decoded PCM, mixing all channels down to mono, and hands it on resampled to
     * [targetRate] in blocks of [BLOCK_SECONDS]. Whole-second blocks resample to an exact length,
     * so timing does not drift over a long file.
     */
    private class MonoBlocks(private val targetRate: Int, private val onAudio: (FloatArray) -> Unit) {
        private var samples = FloatArray(0)
        private var size = 0

        /** The rate of the decoded audio; changing it hands on what was collected at the old rate. */
        var sampleRate = 0
            set(value) {
                if (value != field) flush()
                field = value
            }

        fun appendMono(buffer: java.nio.ByteBuffer, encoding: Int, channels: Int) {
            val frames = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.asFloatBuffer().let { floats ->
                    FloatArray(floats.remaining() / channels) { frame ->
                        var sum = 0f
                        for (c in 0 until channels) sum += floats.get(frame * channels + c)
                        sum / channels
                    }
                }
                else -> buffer.asShortBuffer().let { shorts ->
                    FloatArray(shorts.remaining() / channels) { frame ->
                        var sum = 0f
                        for (c in 0 until channels) sum += shorts.get(frame * channels + c) / PCM16_SCALE
                        sum / channels
                    }
                }
            }
            val blockSize = sampleRate * BLOCK_SECONDS
            if (samples.size < blockSize) samples = samples.copyOf(blockSize)
            var offset = 0
            while (offset < frames.size) {
                val count = minOf(frames.size - offset, blockSize - size)
                frames.copyInto(samples, size, offset, offset + count)
                size += count
                offset += count
                if (size == blockSize) flush()
            }
        }

        fun flush() {
            if (size == 0) return
            onAudio(Resampler.resample(samples.copyOf(size), sampleRate, targetRate))
            size = 0
        }
    }

    private companion object {
        const val TIMEOUT_MICROS = 10_000L
        const val PCM16_SCALE = 32768f
        const val BLOCK_SECONDS = 10
    }
}
