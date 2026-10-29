package io.github.jakub_m_arch.voicemator.files

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Converts mono audio between sample rates; good enough for speech recognition. */
object Resampler {

    fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || samples.isEmpty()) return samples
        // When lowering the rate, average neighbouring samples first so high frequencies do not
        // fold back into the speech band.
        val source = if (fromRate > toRate) boxFilter(samples, ceil(fromRate.toDouble() / toRate).toInt()) else samples
        val ratio = fromRate.toDouble() / toRate
        val length = (samples.size / ratio).roundToInt()
        return FloatArray(length) { i ->
            val position = i * ratio
            val index = floor(position).toInt().coerceAtMost(source.size - 1)
            val next = (index + 1).coerceAtMost(source.size - 1)
            val fraction = (position - index).toFloat()
            source[index] + (source[next] - source[index]) * fraction
        }
    }

    private fun boxFilter(samples: FloatArray, width: Int): FloatArray {
        if (width <= 1) return samples
        val result = FloatArray(samples.size)
        var sum = 0.0
        for (i in samples.indices) {
            sum += samples[i]
            if (i >= width) sum -= samples[i - width]
            result[i] = (sum / minOf(i + 1, width)).toFloat()
        }
        return result
    }
}
