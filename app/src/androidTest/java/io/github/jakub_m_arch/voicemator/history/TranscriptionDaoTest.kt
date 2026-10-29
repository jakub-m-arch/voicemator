package io.github.jakub_m_arch.voicemator.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptionDaoTest {

    private lateinit var database: HistoryDatabase
    private lateinit var dao: TranscriptionDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        dao = database.transcriptions()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun removingTextsKeepsStatistics() = runBlocking {
        dao.insert(entry(text = "Kupić mleko", words = 2))
        dao.insert(entry(text = "Zadzwonić do Ani jutro", words = 4))

        dao.clearTexts()

        assertTrue(dao.observeWithText().first().isEmpty())
        assertEquals(0, dao.observeTextCount().first())
        assertEquals(6, dao.observeUsage().first().sumOf { it.wordCount })
    }

    @Test
    fun entriesWithoutTextAreHiddenButCounted() = runBlocking {
        dao.insert(entry(text = "Widoczne", words = 1))
        dao.insert(entry(text = "", words = 5))

        assertEquals(listOf("Widoczne"), dao.observeWithText().first().map { it.text })
        assertEquals(2, dao.observeUsage().first().size)
    }

    private fun entry(text: String, words: Int) = Transcription(
        createdAt = System.currentTimeMillis(),
        text = text,
        wordCount = words,
        audioDurationMillis = 1_000,
    )
}
