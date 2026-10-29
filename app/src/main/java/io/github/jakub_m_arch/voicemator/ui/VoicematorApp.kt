package io.github.jakub_m_arch.voicemator.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.history.HistoryScreen
import io.github.jakub_m_arch.voicemator.history.StatisticsScreen

private enum class Destination(@StringRes val label: Int, @DrawableRes val icon: Int) {
    History(R.string.tab_history, R.drawable.ic_tab_history),
    Statistics(R.string.tab_statistics, R.drawable.ic_tab_statistics),
    Settings(R.string.tab_settings, R.drawable.ic_tab_settings),
}

/** The app's three tabs. Opens on Settings until dictation is ready to use. */
@Composable
fun VoicematorApp() {
    val context = LocalContext.current
    var destination by rememberSaveable {
        mutableStateOf(if (DictationRequirements.areMet(context)) Destination.History else Destination.Settings)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destination = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { contentPadding ->
        when (destination) {
            Destination.History -> HistoryScreen(contentPadding)
            Destination.Statistics -> StatisticsScreen(contentPadding)
            Destination.Settings -> SetupScreen(contentPadding)
        }
    }
}
