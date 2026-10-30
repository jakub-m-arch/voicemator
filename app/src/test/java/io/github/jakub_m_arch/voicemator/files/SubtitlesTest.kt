package io.github.jakub_m_arch.voicemator.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {

    @Test
    fun splitsSegmentsIntoSentencesWithSharedTimes() {
        val cues = Subtitles.cues(listOf(TranscriptSegment(10_000, 20_000, "Raz dwa. Trzy cztery?")))

        assertEquals(
            listOf(TranscriptSegment(10_000, 14_000, "Raz dwa."), TranscriptSegment(14_000, 20_000, "Trzy cztery?")),
            cues,
        )
    }

    @Test
    fun breaksLongSentencesIntoLines() {
        val sentence = List(30) { "słowo" }.joinToString(" ") + "."
        val cues = Subtitles.cues(listOf(TranscriptSegment(0, 12_000, sentence)))

        assertTrue(cues.size > 1)
        assertTrue(cues.all { it.text.length <= 84 })
        assertEquals(sentence, cues.joinToString(" ") { it.text })
        assertEquals(12_000, cues.last().endMillis)
    }

    @Test
    fun writesSubRip() {
        val transcript = FileTranscript(
            segments = listOf(TranscriptSegment(0, 2_500, "Dzień dobry."), TranscriptSegment(3_725_000, 3_726_040, "Koniec.")),
            durationMillis = 3_726_040,
        )

        assertEquals(
            "1\n00:00:00,000 --> 00:00:02,500\nDzień dobry.\n\n2\n01:02:05,000 --> 01:02:06,040\nKoniec.\n",
            Subtitles.toSrt(transcript),
        )
    }
}
