package io.github.jakub_m_arch.voicemator.asr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class RecordingSegmenterTest {

    @Test
    fun keepsShortRecordingInOnePiece() {
        val segmenter = RecordingSegmenter(SAMPLE_RATE)
        val audio = tone(seconds = 15.0)

        assertTrue(segmenter.append(audio).isEmpty())
        assertArrayEquals(audio, segmenter.flush(), 0f)
        assertNull(segmenter.flush())
    }

    @Test
    fun cutsLongRecordingAtThePause() {
        val segmenter = RecordingSegmenter(SAMPLE_RATE)
        val audio = tone(seconds = 23.0) + FloatArray(SAMPLE_RATE / 2) + tone(seconds = 10.0)

        val chunks = feedInPieces(segmenter, audio)

        assertEquals(1, chunks.size)
        val cutSeconds = chunks.single().size / SAMPLE_RATE.toDouble()
        assertTrue("cut at $cutSeconds s", cutSeconds in 23.0..23.5)
    }

    @Test
    fun keepsEverySampleInOrder() {
        val segmenter = RecordingSegmenter(SAMPLE_RATE, minChunkSeconds = 2, searchSeconds = 1)
        val audio = tone(seconds = 11.3)

        val chunks = feedInPieces(segmenter, audio) + listOfNotNull(segmenter.flush())

        assertTrue(chunks.size > 1)
        chunks.dropLast(1).forEach { assertTrue(it.size in 2 * SAMPLE_RATE..3 * SAMPLE_RATE) }
        assertArrayEquals(audio, chunks.reduce(FloatArray::plus), 0f)
    }

    private fun feedInPieces(segmenter: RecordingSegmenter, audio: FloatArray): List<FloatArray> {
        val piece = SAMPLE_RATE / 20
        return audio.toList().chunked(piece).flatMap { segmenter.append(it.toFloatArray()) }
    }

    /** A tone whose loudness varies, so that no moment is silent. */
    private fun tone(seconds: Double): FloatArray = FloatArray((seconds * SAMPLE_RATE).toInt()) { i ->
        val t = i / SAMPLE_RATE.toFloat()
        (0.3f + 0.2f * abs(sin(1.3f * t))) * sin(2 * Math.PI.toFloat() * 220f * t)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
