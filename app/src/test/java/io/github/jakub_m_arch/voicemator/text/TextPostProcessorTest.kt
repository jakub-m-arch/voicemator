package io.github.jakub_m_arch.voicemator.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {

    private val fillersOnly = TextPostProcessor(emptyList(), removeFillers = true)

    @Test
    fun removesFillersAndTidiesUp() {
        assertEquals("Chciałem zapytać, czy jutro pasuje.", fillersOnly.process("Yyy, chciałem zapytać, eee, czy jutro pasuje."))
        assertEquals("So, I think we should go.", fillersOnly.process("So, um, I think uh we should go."))
    }

    @Test
    fun keepsRealWordsThatLookLikeFillers() {
        val text = "Hmmm to ciekawe. Ten system i ten temat. Emma i Eryk mają rację."
        assertEquals("To ciekawe. Ten system i ten temat. Emma i Eryk mają rację.", fillersOnly.process(text))
    }

    @Test
    fun leavesTextAloneWhenFillerRemovalIsOff() {
        val text = "Yyy, dzień dobry."
        assertEquals(text, TextPostProcessor(emptyList(), removeFillers = false).process(text))
    }

    @Test
    fun replacesWholeWordsIgnoringCase() {
        val processor = TextPostProcessor(listOf(Replacement("voice mator", "Voicemator")), removeFillers = false)

        assertEquals("Testuję Voicemator na Pixelu.", processor.process("Testuję Voice Mator na Pixelu."))
        assertEquals("voice matorek", processor.process("voice matorek"))
    }

    @Test
    fun prefersLongerPhrasesAndKeepsSentenceCapitals() {
        val processor = TextPostProcessor(
            listOf(Replacement("ania", "Anna"), Replacement("ania nowak", "dr Anna Nowak"), Replacement("ok", "okej")),
            removeFillers = false,
        )

        assertEquals("Dzwoniła dr Anna Nowak. Okej, Anna wie.", processor.process("Dzwoniła Ania Nowak. Ok, ania wie."))
    }
}
