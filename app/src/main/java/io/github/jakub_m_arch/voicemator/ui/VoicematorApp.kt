package io.github.jakub_m_arch.voicemator.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationService
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus
import io.github.jakub_m_arch.voicemator.history.HistoryScreen
import io.github.jakub_m_arch.voicemator.history.StatisticsScreen
import io.github.jakub_m_arch.voicemator.ui.settings.SettingsScreen

private enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    History(R.string.tab_history, Icons.Outlined.History),
    Statistics(R.string.tab_statistics, Icons.Outlined.BarChart),
    Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

/** The app's three tabs. Opens on Settings until dictation is ready to use. */
@Composable
fun VoicematorApp() {
    val context = LocalContext.current
    val dictationState by DictationStatus.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable {
        mutableStateOf(if (DictationRequirements.areMet(context)) Destination.History else Destination.Settings)
    }

    Scaffold(
        floatingActionButton = {
            if (destination == Destination.History && dictationState != DictationState.Transcribing) {
                val isRecording = dictationState == DictationState.Recording
                ExtendedFloatingActionButton(
                    onClick = {
                        when {
                            isRecording -> context.startService(DictationService.stopIntent(context))
                            DictationRequirements.areMet(context) ->
                                ContextCompat.startForegroundService(context, DictationService.startIntent(context))
                            else -> destination = Destination.Settings
                        }
                    },
                    icon = { Icon(if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = null) },
                    text = { Text(stringResource(if (isRecording) R.string.button_stop else R.string.button_dictate)) },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { contentPadding ->
        when (destination) {
            Destination.History -> HistoryScreen(contentPadding)
            Destination.Statistics -> StatisticsScreen(contentPadding)
            Destination.Settings -> SettingsScreen(contentPadding)
        }
    }
}
