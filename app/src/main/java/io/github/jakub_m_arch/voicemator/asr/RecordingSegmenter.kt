package io.github.jakub_m_arch.voicemator.asr

/**
 * Splits a growing recording into chunks that can be transcribed while the user keeps talking.
 * Chunks are kept short enough that a live preview of the current one can be refreshed every
 * second or so on a phone.
 *
 * Once more than [minChunkSeconds] + [searchSeconds] of audio is pending, a chunk is cut at the
 * quietest moment between [minChunkSeconds] and that limit, which is almost always a pause
 * between words. Shorter recordings stay in one piece until [flush].
 */
class RecordingSegmenter(
    private val sampleRate: Int,
    minChunkSeconds: Int = 10,
    searchSeconds: Int = 5,
) {
    private val minChunkSamples = minChunkSeconds * sampleRate
    private val maxChunkSamples = (minChunkSeconds + searchSeconds) * sampleRate
    private val frameSamples = sampleRate / FRAMES_PER_SECOND

    private var pending = FloatArray(maxChunkSamples + sampleRate)
    private var pendingSize = 0

    /** Adds [count] samples and returns any chunks that became ready, oldest first. */
    fun append(samples: FloatArray, count: Int = samples.size): List<FloatArray> {
        ensureCapacity(pendingSize + count)
        samples.copyInto(pending, destinationOffset = pendingSize, endIndex = count)
        pendingSize += count

        val chunks = mutableListOf<FloatArray>()
        while (pendingSize >= maxChunkSamples) {
            val cut = quietestCutPoint()
            chunks += pending.copyOf(cut)
            pending.copyInto(pending, destinationOffset = 0, startIndex = cut, endIndex = pendingSize)
            pendingSize -= cut
        }
        return chunks
    }

    /** Audio not yet returned as a chunk, for a live preview; it stays pending. */
    fun pending(): FloatArray = pending.copyOf(pendingSize)

    /** Returns the remaining audio, or null if there is none. */
    fun flush(): FloatArray? {
        if (pendingSize == 0) return null
        return pending.copyOf(pendingSize).also { pendingSize = 0 }
    }

    /** Finds the quietest [WINDOW_FRAMES]-frame window in the search range and cuts at its middle. */
    private fun quietestCutPoint(): Int {
        val windowSamples = frameSamples * WINDOW_FRAMES
        var bestStart = minChunkSamples
        var bestEnergy = Double.MAX_VALUE
        var start = minChunkSamples
        while (start + windowSamples <= maxChunkSamples) {
            var energy = 0.0
            for (i in start until start + windowSamples) energy += pending[i] * pending[i]
            if (energy < bestEnergy) {
                bestEnergy = energy
                bestStart = start
            }
            start += frameSamples
        }
        return bestStart + windowSamples / 2
    }

    private fun ensureCapacity(required: Int) {
        if (required > pending.size) pending = pending.copyOf(maxOf(required, pending.size * 2))
    }

    private companion object {
        const val FRAMES_PER_SECOND = 10
        const val WINDOW_FRAMES = 3
    }
}
