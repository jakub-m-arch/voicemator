package io.github.jakub_m_arch.voicemator.files

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.MainActivity
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.history.HistoryMode
import io.github.jakub_m_arch.voicemator.history.HistoryRepository
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transcribes an audio file shared to Voicemator or opened with it, such as a voice message or a
 * recorded meeting. The text is shown for copying, sharing or saving as a text or subtitle file;
 * it is added to the history only when the user asks.
 */
class TranscribeFileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = audioUri(intent)
        setContent {
            VoicematorTheme {
                TranscribeFileScreen(uri = uri, onClose = ::finish)
            }
        }
    }

    private fun audioUri(intent: Intent): Uri? = when (intent.action) {
        // Some apps put the file only in the clip data, which also carries the read permission.
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        else -> intent.data
    }

    companion object {
        /** Opens [uri], which the caller must have granted read access to, for transcription. */
        fun intent(context: android.content.Context, uri: Uri): Intent =
            Intent(context, TranscribeFileActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

sealed interface FileTranscriptionState {
    data object ModelMissing : FileTranscriptionState
    data class Working(val progress: FileTranscriber.Progress?) : FileTranscriptionState
    data class Done(val transcript: FileTranscript, val isSavedToHistory: Boolean = false) : FileTranscriptionState
    data class Failed(val reason: Reason) : FileTranscriptionState

    enum class Reason { NoFile, Unsupported }
}

enum class ExportFormat(val mimeType: String, val extension: String) {
    Text("text/plain", "txt"),
    Subtitles("application/x-subrip", "srt"),
}

class TranscribeFileViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow<FileTranscriptionState>(FileTranscriptionState.Working(null))
    private val history = HistoryRepository.get(application)
    private var job: Job? = null

    val state: StateFlow<FileTranscriptionState> = mutableState.asStateFlow()
    val historyMode: StateFlow<HistoryMode> = history.mode

    fun start(uri: Uri?) {
        if (job != null) return
        val context = getApplication<Application>()
        when {
            uri == null -> mutableState.value = FileTranscriptionState.Failed(FileTranscriptionState.Reason.NoFile)
            !ModelStorage(context).isInstalled() -> mutableState.value = FileTranscriptionState.ModelMissing
            else -> job = viewModelScope.launch(Dispatchers.Default) {
                mutableState.value = try {
                    val transcript = FileTranscriber(context).transcribe(uri) { progress ->
                        mutableState.value = FileTranscriptionState.Working(progress)
                    }
                    FileTranscriptionState.Done(transcript)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not transcribe $uri", e)
                    FileTranscriptionState.Failed(FileTranscriptionState.Reason.Unsupported)
                }
            }
        }
    }

    /** Adds the transcript to the history, as its current mode allows. */
    fun saveToHistory() {
        val done = mutableState.value as? FileTranscriptionState.Done ?: return
        if (done.isSavedToHistory) return
        mutableState.value = done.copy(isSavedToHistory = true)
        viewModelScope.launch(Dispatchers.IO) {
            history.record(done.transcript.text, done.transcript.durationMillis)
        }
    }

    /** Writes the transcript to [target], a document the user just created. */
    fun export(target: Uri, format: ExportFormat) {
        val transcript = (mutableState.value as? FileTranscriptionState.Done)?.transcript ?: return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val content = when (format) {
                        ExportFormat.Text -> transcript.text + "\n"
                        ExportFormat.Subtitles -> Subtitles.toSrt(transcript)
                    }
                    checkNotNull(context.contentResolver.openOutputStream(target, "wt")).bufferedWriter().use { it.write(content) }
                }.onFailure { Log.e(TAG, "Could not save $target", it) }.isSuccess
            }
            val message = if (saved) R.string.transcribe_file_saved else R.string.transcribe_file_save_failed
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "TranscribeFile"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscribeFileScreen(uri: Uri?, onClose: () -> Unit, viewModel: TranscribeFileViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val historyMode by viewModel.historyMode.collectAsStateWithLifecycle()
    LaunchedEffect(uri) { viewModel.start(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transcribe_file_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.button_back)) } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val name = remember(uri) { uri?.let { fileName(context, it) } }
            name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            when (val current = state) {
                is FileTranscriptionState.Working -> WorkingStatus(current.progress)
                is FileTranscriptionState.Done -> Result(
                    state = current,
                    sourceName = name,
                    canSaveToHistory = historyMode != HistoryMode.Off,
                    onSaveToHistory = viewModel::saveToHistory,
                    onExport = viewModel::export,
                    onClose = onClose,
                )
                FileTranscriptionState.ModelMissing -> {
                    Text(stringResource(R.string.transcribe_file_model_missing))
                    Button(onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java))
                        onClose()
                    }) { Text(stringResource(R.string.transcribe_file_open_app)) }
                }
                is FileTranscriptionState.Failed -> Text(
                    stringResource(
                        when (current.reason) {
                            FileTranscriptionState.Reason.NoFile -> R.string.transcribe_file_no_file
                            FileTranscriptionState.Reason.Unsupported -> R.string.transcribe_file_unsupported
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WorkingStatus(progress: FileTranscriber.Progress?) {
    // Long recordings take minutes; a sleeping screen would let Android stop the work.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val fraction = progress?.fraction
    Text(
        when {
            progress == null -> stringResource(R.string.transcribe_file_starting)
            fraction == null -> stringResource(R.string.transcribe_file_transcribing_unknown)
            else -> stringResource(R.string.transcribe_file_transcribing, (fraction * 100).roundToInt())
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
    if (!progress?.text.isNullOrEmpty()) {
        Text(
            progress.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Result(
    state: FileTranscriptionState.Done,
    sourceName: String?,
    canSaveToHistory: Boolean,
    onSaveToHistory: () -> Unit,
    onExport: (Uri, ExportFormat) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val text = state.transcript.text
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.Text.mimeType)) { target ->
        if (target != null) onExport(target, ExportFormat.Text)
    }
    val saveSubtitles = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.Subtitles.mimeType)) { target ->
        if (target != null) onExport(target, ExportFormat.Subtitles)
    }
    val baseName = sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: DEFAULT_EXPORT_NAME

    if (text.isEmpty()) {
        Text(stringResource(R.string.notification_nothing_heard))
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Transcription", text))
        }) { Text(stringResource(R.string.history_copy)) }
        OutlinedButton(onClick = {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(share, null))
        }) { Text(stringResource(R.string.transcribe_file_share)) }
        OutlinedButton(onClick = { saveText.launch("$baseName.${ExportFormat.Text.extension}") }) {
            Text(stringResource(R.string.transcribe_file_save_txt))
        }
        OutlinedButton(onClick = { saveSubtitles.launch("$baseName.${ExportFormat.Subtitles.extension}") }) {
            Text(stringResource(R.string.transcribe_file_save_srt))
        }
        if (canSaveToHistory) {
            OutlinedButton(onClick = onSaveToHistory, enabled = !state.isSavedToHistory) {
                Text(
                    stringResource(
                        if (state.isSavedToHistory) R.string.transcribe_file_saved_to_history else R.string.transcribe_file_save_to_history,
                    ),
                )
            }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.transcribe_file_done)) }
    }
}

private const val DEFAULT_EXPORT_NAME = "transcription"

private fun fileName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
