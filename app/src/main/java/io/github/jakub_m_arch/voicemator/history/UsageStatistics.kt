package io.github.jakub_m_arch.voicemator.history

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Dictation statistics derived from the history, by local calendar day. */
data class UsageStatistics(
    val totalWords: Int,
    val totalDictations: Int,
    val wordsToday: Int,
    /** Days in a row, ending today or yesterday, with at least one dictation. */
    val streakDays: Int,
    /** Estimated typing time minus the time spent speaking, never negative. */
    val minutesSaved: Int,
    /** Average speaking pace, or null without recorded audio. */
    val wordsPerMinute: Int?,
    /** Words per day for the last [RECENT_DAYS] days, oldest first. */
    val recentDays: List<DayWords>,
    /** Weeks for the heatmap, oldest first; each week runs Monday to Sunday. */
    val heatmapWeeks: List<List<DayWords>>,
) {
    data class DayWords(val date: LocalDate, val words: Int, val isFuture: Boolean = false)

    companion object {
        /** Typical typing speed on a phone keyboard, used to estimate time saved. */
        const val TYPING_WORDS_PER_MINUTE = 36
        const val RECENT_DAYS = 14
        const val HEATMAP_WEEKS = 26

        fun compute(entries: List<UsageEntry>, today: LocalDate, zone: ZoneId): UsageStatistics {
            val wordsByDay = entries
                .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
                .mapValues { (_, day) -> day.sumOf { it.wordCount } }

            val totalWords = entries.sumOf { it.wordCount }
            val speakingMinutes = entries.sumOf { it.audioDurationMillis } / MILLIS_PER_MINUTE
            val typingMinutes = totalWords.toDouble() / TYPING_WORDS_PER_MINUTE

            return UsageStatistics(
                totalWords = totalWords,
                totalDictations = entries.size,
                wordsToday = wordsByDay[today] ?: 0,
                streakDays = streak(wordsByDay.keys, today),
                minutesSaved = (typingMinutes - speakingMinutes).coerceAtLeast(0.0).roundToInt(),
                wordsPerMinute = if (speakingMinutes > 0) (totalWords / speakingMinutes).roundToInt() else null,
                recentDays = (RECENT_DAYS - 1 downTo 0).map { offset ->
                    val date = today.minusDays(offset.toLong())
                    DayWords(date, wordsByDay[date] ?: 0)
                },
                heatmapWeeks = heatmap(wordsByDay, today),
            )
        }

        private fun streak(activeDays: Set<LocalDate>, today: LocalDate): Int {
            var day = if (today in activeDays) today else today.minusDays(1)
            var count = 0
            while (day in activeDays) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

        private fun heatmap(wordsByDay: Map<LocalDate, Int>, today: LocalDate): List<List<DayWords>> {
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val firstMonday = thisMonday.minusWeeks((HEATMAP_WEEKS - 1).toLong())
            return (0 until HEATMAP_WEEKS).map { week ->
                (0 until DAYS_PER_WEEK).map { dayOfWeek ->
                    val date = firstMonday.plusDays((week * DAYS_PER_WEEK + dayOfWeek).toLong())
                    DayWords(date, wordsByDay[date] ?: 0, isFuture = date.isAfter(today))
                }
            }
        }

        private const val MILLIS_PER_MINUTE = 60_000.0
        private const val DAYS_PER_WEEK = 7
    }
}
