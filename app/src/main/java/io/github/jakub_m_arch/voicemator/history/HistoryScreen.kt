package io.github.jakub_m_arch.voicemator.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(contentPadding: PaddingValues, viewModel: HistoryViewModel = viewModel()) {
    val transcriptions by viewModel.transcriptions.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    // In statistics-only mode nothing is shown, not even texts saved before the switch.
    val items = if (mode == HistoryMode.StatisticsOnly) emptyList() else transcriptions ?: return
    val zone = ZoneId.systemDefault()
    val byDay = items.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        }
        if (mode != HistoryMode.Full) {
            item {
                Text(
                    stringResource(
                        if (mode == HistoryMode.StatisticsOnly) R.string.history_statistics_only else R.string.history_disabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (items.isEmpty() && mode == HistoryMode.Full) {
            item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        byDay.forEach { (day, dayItems) ->
            item(key = day.toEpochDay()) {
                Text(
                    dayLabel(day, LocalDate.now(zone)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(dayItems, key = { it.id }) { transcription ->
                TranscriptionCard(transcription, zone, onDelete = { viewModel.delete(transcription) })
            }
        }
    }
}

@Composable
private fun TranscriptionCard(transcription: Transcription, zone: ZoneId, onDelete: () -> Unit) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable(transcription.id) { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(transcription.createdAt).atZone(zone).toLocalTime()

    Card(modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                transcription.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = { copy(context, transcription.text) }) {
                    Text(stringResource(R.string.history_copy))
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.history_delete)) }
            }
        }
    }
}

/** History settings, shown on the Settings tab. */
@Composable
fun HistorySettingsCard(viewModel: HistoryViewModel = viewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val textCount by viewModel.textCount.collectAsStateWithLifecycle()
    var pendingMode by rememberSaveable { mutableStateOf<HistoryMode?>(null) }
    var isConfirmingRemoveTexts by rememberSaveable { mutableStateOf(false) }
    var isConfirmingClear by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_settings_body), style = MaterialTheme.typography.bodyMedium)
            Column(modifier = Modifier.selectableGroup()) {
                HistoryMode.entries.forEach { option ->
                    ModeOption(
                        mode = option,
                        isSelected = option == mode,
                        onSelect = {
                            // Leaving full history: offer to erase texts that are already saved.
                            if (option != HistoryMode.Full && mode == HistoryMode.Full && textCount > 0) {
                                pendingMode = option
                            } else {
                                viewModel.setMode(option)
                            }
                        },
                    )
                }
            }
            if (textCount > 0) {
                OutlinedButton(onClick = { isConfirmingRemoveTexts = true }) {
                    Text(stringResource(R.string.history_remove_texts))
                }
            }
            OutlinedButton(onClick = { isConfirmingClear = true }) {
                Text(stringResource(R.string.history_clear))
            }
        }
    }

    pendingMode?.let { newMode ->
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text(stringResource(R.string.history_mode_change_title)) },
            text = { Text(stringResource(R.string.history_mode_change_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeTexts()
                    viewModel.setMode(newMode)
                    pendingMode = null
                }) { Text(stringResource(R.string.history_remove_texts)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setMode(newMode)
                    pendingMode = null
                }) { Text(stringResource(R.string.history_keep_texts)) }
            },
        )
    }

    if (isConfirmingRemoveTexts) {
        ConfirmDialog(
            title = stringResource(R.string.history_remove_texts_confirm_title),
            body = stringResource(R.string.history_remove_texts_confirm_body),
            confirmLabel = stringResource(R.string.history_remove_texts),
            onConfirm = viewModel::removeTexts,
            onDismiss = { isConfirmingRemoveTexts = false },
        )
    }

    if (isConfirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_confirm_title),
            body = stringResource(R.string.history_clear_confirm_body),
            confirmLabel = stringResource(R.string.history_clear),
            onConfirm = viewModel::clear,
            onDismiss = { isConfirmingClear = false },
        )
    }
}

@Composable
private fun ModeOption(mode: HistoryMode, isSelected: Boolean, onSelect: () -> Unit) {
    val (title, description) = when (mode) {
        HistoryMode.Full -> R.string.history_mode_full to R.string.history_mode_full_body
        HistoryMode.StatisticsOnly -> R.string.history_mode_statistics to R.string.history_mode_statistics_body
        HistoryMode.Off -> R.string.history_mode_off to R.string.history_mode_off_body
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
    )
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.history_today)
    today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    // Android shows its own confirmation for copies.
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
}

private const val COLLAPSED_LINES = 4
private const val CLIP_LABEL = "Transcription"
