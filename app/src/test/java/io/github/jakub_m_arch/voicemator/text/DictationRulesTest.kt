package io.github.jakub_m_arch.voicemator.text

import io.github.jakub_m_arch.voicemator.profiles.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationRulesTest {

    private val messengers = Profile(id = 1, name = "Messengers", capitalizeFirst = false, endWithPeriod = false, finalPass = true)
    private val email = Profile(id = 2, name = "Email")

    private val rules = DictationRules(
        dictionary = listOf(
            DictionaryEntry(heard = "ania", written = "Anna"),
            DictionaryEntry(heard = "ania", written = "Aniu", profileId = messengers.id),
            DictionaryEntry(heard = "pozdro", written = "Pozdrawiam serdecznie", profileId = email.id),
        ),
        profiles = listOf(messengers, email),
        appProfiles = mapOf("com.whatsapp" to messengers.id, "com.google.android.gm" to email.id),
        removeFillers = true,
        finalPass = false,
    )

    @Test
    fun findsTheProfileOfTheApp() {
        assertEquals(messengers, rules.profileFor("com.whatsapp"))
        assertEquals(email, rules.profileFor("com.google.android.gm"))
        assertNull(rules.profileFor("com.example.notes"))
        assertNull(rules.profileFor(null))
    }

    @Test
    fun appliesTheProfileStyleAndItsOwnDictionaryFirst() {
        assertEquals("cześć Aniu, będę o 5", rules.postProcessor(messengers).process("Cześć ania, będę o 5."))
        assertEquals("Cześć Anna. Pozdrawiam serdecznie.", rules.postProcessor(email).process("Cześć ania. Pozdro."))
    }

    @Test
    fun withoutProfileUsesOnlyTheGeneralDictionary() {
        assertEquals("Cześć Anna. Pozdro.", rules.postProcessor(null).process("Cześć ania. Pozdro."))
    }

    @Test
    fun profileCanOverrideTheFinalPass() {
        assertTrue(rules.finalPass(messengers))
        assertFalse(rules.finalPass(email))
        assertFalse(rules.finalPass(null))
    }
}
