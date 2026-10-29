package io.github.jakub_m_arch.voicemator.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ResamplerTest {

    @Test
    fun keepsAudioAtTheSameRate() {
        val audio = FloatArray(100) { it / 100f }
        assertSame(audio, Resampler.resample(audio, 16_000, 16_000))
    }

    @Test
    fun keepsDurationWhenDownsampling() {
        val audio = tone(rate = 48_000, seconds = 2.0, frequency = 300.0)

        val resampled = Resampler.resample(audio, 48_000, 16_000)

        assertEquals(32_000, resampled.size)
    }

    @Test
    fun preservesSpeechFrequencies() {
        val resampled = Resampler.resample(tone(rate = 44_100, seconds = 1.0, frequency = 300.0), 44_100, 16_000)
        val expected = tone(rate = 16_000, seconds = 1.0, frequency = 300.0)

        // Compare away from the edges; the box filter attenuates a 300 Hz tone only slightly.
        val error = (1_000 until 15_000).maxOf { abs(resampled[it] - expected[it]) }
        assertEquals(0f, error, 0.1f)
    }

    @Test
    fun upsamplesByInterpolating() {
        val resampled = Resampler.resample(floatArrayOf(0f, 1f), 8_000, 16_000)
        assertEquals(4, resampled.size)
        assertEquals(0.5f, resampled[1], 0.001f)
    }

    private fun tone(rate: Int, seconds: Double, frequency: Double) =
        FloatArray((rate * seconds).toInt()) { i -> (0.5 * sin(2 * PI * frequency * i / rate)).toFloat() }
}
