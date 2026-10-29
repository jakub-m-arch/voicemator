package io.github.jakub_m_arch.voicemator.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class UsageStatisticsTest {

    @Test
    fun emptyHistory() {
        val stats = UsageStatistics.compute(emptyList(), TODAY, ZONE)

        assertEquals(0, stats.totalWords)
        assertEquals(0, stats.streakDays)
        assertEquals(0, stats.minutesSaved)
        assertNull(stats.wordsPerMinute)
        assertEquals(UsageStatistics.RECENT_DAYS, stats.recentDays.size)
        assertEquals(UsageStatistics.HEATMAP_WEEKS, stats.heatmapWeeks.size)
    }

    @Test
    fun groupsWordsByLocalDay() {
        val entries = listOf(
            entry(TODAY, LocalTime.of(0, 30), words = 10),
            entry(TODAY, LocalTime.of(23, 30), words = 5),
            entry(TODAY.minusDays(1), LocalTime.of(23, 59), words = 7),
        )

        val stats = UsageStatistics.compute(entries, TODAY, ZONE)

        assertEquals(15, stats.wordsToday)
        assertEquals(22, stats.totalWords)
        assertEquals(TODAY, stats.recentDays.last().date)
        assertEquals(15, stats.recentDays.last().words)
        assertEquals(7, stats.recentDays[stats.recentDays.size - 2].words)
    }

    @Test
    fun streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        val days = listOf(1L, 2L, 3L, 5L).map { entry(TODAY.minusDays(it), NOON, words = 1) }

        assertEquals(3, UsageStatistics.compute(days, TODAY, ZONE).streakDays)
        assertEquals(0, UsageStatistics.compute(days, TODAY.plusDays(1), ZONE).streakDays)
    }

    @Test
    fun timeSavedSubtractsSpeakingTime() {
        // 360 words take 10 minutes to type; speaking them took 2 minutes.
        val entries = listOf(entry(TODAY, NOON, words = 360, audioMillis = 120_000))

        val stats = UsageStatistics.compute(entries, TODAY, ZONE)

        assertEquals(8, stats.minutesSaved)
        assertEquals(180, stats.wordsPerMinute)
    }

    @Test
    fun heatmapWeeksRunMondayToSundayAndEndThisWeek() {
        val stats = UsageStatistics.compute(listOf(entry(TODAY, NOON, words = 4)), TODAY, ZONE)
        val days = stats.heatmapWeeks.flatten()

        assertTrue(stats.heatmapWeeks.all { week -> week.first().date.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(stats.heatmapWeeks.all { it.size == 7 })
        assertEquals(4, days.single { it.date == TODAY }.words)
        assertTrue(days.filter { it.date.isAfter(TODAY) }.all { it.isFuture })
        assertTrue(days.last().date >= TODAY)
    }

    @Test
    fun countsWordsInPolishText() {
        assertEquals(6, countWords("Dzień dobry, to jest test — dyktowania."))
        assertEquals(3, countWords("Mam 1000 zł"))
        assertEquals(2, countWords("e-mail don't"))
        assertEquals(0, countWords("  …  "))
    }

    private fun entry(date: LocalDate, time: LocalTime, words: Int, audioMillis: Long = 0) = UsageEntry(
        createdAt = date.atTime(time).atZone(ZONE).toInstant().toEpochMilli(),
        wordCount = words,
        audioDurationMillis = audioMillis,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")
        val TODAY: LocalDate = LocalDate.of(2026, 10, 29)
        val NOON: LocalTime = LocalTime.NOON
    }
}
