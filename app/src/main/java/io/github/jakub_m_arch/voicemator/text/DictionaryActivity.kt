package io.github.jakub_m_arch.voicemator.text

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.history.HistoryDatabase
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lets the user teach Voicemator words it gets wrong: "when you hear X, write Y". Opened for an
 * app profile, it edits the entries that apply only in that profile's apps.
 */
class DictionaryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoicematorTheme {
                DictionaryScreen(onClose = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"

        /** Opens the dictionary of the profile with [profileId], or the general one when null. */
        fun intent(context: Context, profileId: Long? = null): Intent =
            Intent(context, DictionaryActivity::class.java).apply {
                if (profileId != null) putExtra(EXTRA_PROFILE_ID, profileId)
            }
    }
}

class DictionaryViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {

    private val database = HistoryDatabase.get(application)
    private val dao = database.dictionary()

    /** The profile whose entries are shown, or null for the general dictionary. */
    val profileId: Long? = savedState.get<Long>(DictionaryActivity.EXTRA_PROFILE_ID)

    val profileName: StateFlow<String?> =
        (if (profileId == null) flowOf(null) else database.profiles().observe(profileId).map { it?.name })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val entries: StateFlow<List<DictionaryEntry>> = dao.observe(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun add(heard: String, written: String) {
        if (heard.isBlank() || written.isBlank()) return
        viewModelScope.launch {
            dao.insert(DictionaryEntry(heard = heard.trim(), written = written.trim(), profileId = profileId))
        }
    }

    fun delete(entry: DictionaryEntry) {
        viewModelScope.launch { dao.delete(entry.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryScreen(onClose: () -> Unit, viewModel: DictionaryViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    var heard by rememberSaveable { mutableStateOf("") }
    var written by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(profileName?.let { stringResource(R.string.dictionary_title_profile, it) } ?: stringResource(R.string.dictionary_title))
                },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.button_back)) } },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(if (viewModel.profileId == null) R.string.dictionary_intro else R.string.dictionary_intro_profile),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heard,
                            onValueChange = { heard = it },
                            label = { Text(stringResource(R.string.dictionary_heard)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = written,
                            onValueChange = { written = it },
                            label = { Text(stringResource(R.string.dictionary_written)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                viewModel.add(heard, written)
                                heard = ""
                                written = ""
                            },
                            enabled = heard.isNotBlank() && written.isNotBlank(),
                        ) { Text(stringResource(R.string.dictionary_add)) }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.dictionary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(entry.heard, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "→ ${entry.written}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = { viewModel.delete(entry) }) {
                        Text(stringResource(R.string.history_delete))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
