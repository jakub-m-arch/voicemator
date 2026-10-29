package io.github.jakub_m_arch.voicemator.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewestWordsTest {

    @Test
    fun shortTextIsUnchanged() {
        assertEquals("Kupić mleko", RecordingOverlay.newestWords("Kupić mleko", maxChars = 20))
    }

    @Test
    fun longTextKeepsTheEndFromAWordBoundary() {
        val caption = RecordingOverlay.newestWords("Gdy dzwonisz do osoby, która jest daleko", maxChars = 20)

        assertEquals("…która jest daleko", caption)
        assertTrue(caption.length <= 21)
    }
}
