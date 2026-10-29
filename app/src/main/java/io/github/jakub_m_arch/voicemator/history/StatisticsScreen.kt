package io.github.jakub_m_arch.voicemator.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.history.UsageStatistics.DayWords
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatisticsScreen(contentPadding: PaddingValues, viewModel: HistoryViewModel = viewModel()) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val stats = statistics ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium)
        if (stats.totalDictations == 0) {
            Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.stats_words_today),
                value = formatNumber(stats.wordsToday),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_total_words),
                value = formatNumber(stats.totalWords),
                caption = pluralStringResource(
                    R.plurals.stats_dictations,
                    stats.totalDictations,
                    formatNumber(stats.totalDictations),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.stats_streak),
                value = pluralStringResource(R.plurals.stats_days, stats.streakDays, stats.streakDays),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_pace),
                value = stats.wordsPerMinute?.let { stringResource(R.string.stats_pace_value, it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        StatTile(
            label = stringResource(R.string.stats_time_saved),
            value = formatDuration(stats.minutesSaved),
            caption = stringResource(R.string.stats_time_saved_note, UsageStatistics.TYPING_WORDS_PER_MINUTE),
            modifier = Modifier.fillMaxWidth(),
        )

        ChartCard(title = stringResource(R.string.stats_last_days)) { DailyBarChart(stats.recentDays) }
        ChartCard(title = stringResource(R.string.stats_activity)) { ActivityHeatmap(stats.heatmapWeeks) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Words per day as columns; tapping a column shows its exact value. */
@Composable
private fun DailyBarChart(days: List<DayWords>) {
    var selectedIndex by remember(days) { mutableStateOf<Int?>(null) }
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val maxWords = days.maxOf { it.words }.coerceAtLeast(1)

    val selected = selectedIndex?.let(days::get)
    Text(
        if (selected != null) {
            stringResource(R.string.stats_day_detail, shortDate(selected.date), wordsText(selected.words))
        } else {
            wordsText(days.sumOf { it.words })
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(days) {
                detectTapGestures { offset ->
                    val index = (offset.x / (size.width / days.size)).toInt().coerceIn(days.indices)
                    selectedIndex = if (selectedIndex == index) null else index
                }
            },
    ) {
        val slot = size.width / days.size
        val barWidth = slot * BAR_WIDTH_RATIO
        val radius = CornerRadius(4.dp.toPx())
        val minHeight = 2.dp.toPx()
        days.forEachIndexed { index, day ->
            if (day.words == 0) return@forEachIndexed
            val height = (size.height * day.words / maxWords).coerceAtLeast(minHeight)
            val left = index * slot + (slot - barWidth) / 2
            val bar = RoundRect(
                left = left,
                top = size.height - height,
                right = left + barWidth,
                bottom = size.height,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
            )
            val isDimmed = selectedIndex != null && selectedIndex != index
            drawPath(Path().apply { addRoundRect(bar) }, barColor.copy(alpha = if (isDimmed) DIMMED_ALPHA else 1f))
        }
        drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        AxisLabel(shortDate(days.first().date))
        Spacer(Modifier.weight(1f))
        AxisLabel(shortDate(days.last().date))
    }
}

/** A calendar of the last weeks, one column per week, shaded by words dictated that day. */
@Composable
private fun ActivityHeatmap(weeks: List<List<DayWords>>) {
    var selected by remember(weeks) { mutableStateOf<DayWords?>(null) }
    val levels = remember(weeks) { HeatmapLevels(weeks.flatten().filterNot { it.isFuture }.map { it.words }) }
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ALPHA)
    val outline = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val current = selected
    Text(
        if (current != null) {
            stringResource(R.string.stats_day_detail, longDate(current.date), wordsText(current.words))
        } else {
            stringResource(R.string.stats_tap_hint)
        },
        style = MaterialTheme.typography.bodyMedium,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val dayLabelWidth = 32.dp
        val gap = 2.dp
        val cell = min((maxWidth - dayLabelWidth) / weeks.size - gap, MAX_CELL)
        val monthRow = 16.dp
        Row {
            Column(modifier = Modifier.width(dayLabelWidth).padding(top = monthRow)) {
                DayOfWeek.entries.forEach { day ->
                    Box(modifier = Modifier.height(cell + gap)) {
                        if (day in LABELED_DAYS) AxisLabel(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .width((cell + gap) * weeks.size)
                    .height(monthRow + (cell + gap) * DAYS_PER_WEEK)
                    .pointerInput(weeks) {
                        detectTapGestures { offset ->
                            val step = (cell + gap).toPx()
                            val week = (offset.x / step).toInt()
                            val day = ((offset.y - monthRow.toPx()) / step).toInt()
                            val tapped = weeks.getOrNull(week)?.getOrNull(day)
                            selected = tapped?.takeUnless { it.isFuture || it == selected }
                        }
                    },
            ) {
                val step = (cell + gap).toPx()
                val cellSize = Size(cell.toPx(), cell.toPx())
                val radius = CornerRadius(2.dp.toPx())
                val top = monthRow.toPx()
                weeks.forEachIndexed { weekIndex, week ->
                    val firstDay = week.first().date
                    if (weekIndex == 0 || firstDay.month != weeks[weekIndex - 1].first().date.month) {
                        drawText(
                            textMeasurer,
                            firstDay.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()),
                            topLeft = Offset(weekIndex * step, 0f),
                            style = labelStyle,
                        )
                    }
                    week.forEachIndexed { dayIndex, day ->
                        if (day.isFuture) return@forEachIndexed
                        val topLeft = Offset(weekIndex * step, top + dayIndex * step)
                        val level = levels.levelOf(day.words)
                        val color = if (level == 0) emptyColor else primary.copy(alpha = LEVEL_ALPHAS[level - 1])
                        drawRoundRect(color, topLeft, cellSize, radius)
                        if (day == current) drawRoundRect(outline, topLeft, cellSize, radius, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        AxisLabel(stringResource(R.string.stats_less))
        LegendSwatch(emptyColor)
        LEVEL_ALPHAS.forEach { LegendSwatch(primary.copy(alpha = it)) }
        AxisLabel(stringResource(R.string.stats_more))
    }
}

/** Splits non-zero daily word counts into four levels by quartile. */
private class HeatmapLevels(words: List<Int>) {
    private val thresholds: List<Int> = words.filter { it > 0 }.sorted().let { sorted ->
        if (sorted.isEmpty()) emptyList() else QUARTILES.map { sorted[((sorted.size - 1) * it).toInt()] }
    }

    fun levelOf(words: Int): Int = when {
        words <= 0 -> 0
        thresholds.isEmpty() -> 1
        else -> 1 + thresholds.count { words > it }
    }

    private companion object {
        val QUARTILES = listOf(0.25, 0.5, 0.75)
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
private fun AxisLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun wordsText(words: Int): String =
    pluralStringResource(R.plurals.stats_words, words, formatNumber(words))

@Composable
private fun formatDuration(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.stats_minutes, minutes)
    } else {
        stringResource(R.string.stats_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private val MAX_CELL: Dp = 14.dp
private val LABELED_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
private val LEVEL_ALPHAS = listOf(0.3f, 0.5f, 0.75f, 1f)
private const val EMPTY_ALPHA = 0.08f
private const val DIMMED_ALPHA = 0.4f
private const val BAR_WIDTH_RATIO = 0.6f
private const val DAYS_PER_WEEK = 7
private const val MINUTES_PER_HOUR = 60
