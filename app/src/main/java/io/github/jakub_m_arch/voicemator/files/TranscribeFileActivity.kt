package io.github.jakub_m_arch.voicemator.files

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.ui.theme.VoicematorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Transcribes an audio file shared to Voicemator or opened with it, such as a voice message.
 * The text is shown for copying or sharing; it is not added to the dictation history.
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
    data class Done(val text: String) : FileTranscriptionState
    data class Failed(val reason: Reason) : FileTranscriptionState

    enum class Reason { NoFile, Unsupported, TooLong }
}

class TranscribeFileViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow<FileTranscriptionState>(FileTranscriptionState.Working(null))
    private var job: Job? = null

    val state: StateFlow<FileTranscriptionState> = mutableState.asStateFlow()

    fun start(uri: Uri?) {
        if (job != null) return
        val context = getApplication<Application>()
        when {
            uri == null -> mutableState.value = FileTranscriptionState.Failed(FileTranscriptionState.Reason.NoFile)
            !ModelStorage(context).isInstalled() -> mutableState.value = FileTranscriptionState.ModelMissing
            else -> job = viewModelScope.launch(Dispatchers.Default) {
                mutableState.value = try {
                    val text = FileTranscriber(context).transcribe(uri) { progress ->
                        mutableState.value = FileTranscriptionState.Working(progress)
                    }
                    FileTranscriptionState.Done(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AudioFileDecoder.TooLongException) {
                    FileTranscriptionState.Failed(FileTranscriptionState.Reason.TooLong)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not transcribe $uri", e)
                    FileTranscriptionState.Failed(FileTranscriptionState.Reason.Unsupported)
                }
            }
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
            uri?.let { fileName(context, it) }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            when (val current = state) {
                is FileTranscriptionState.Working -> WorkingStatus(current.progress)
                is FileTranscriptionState.Done -> Result(current.text, onClose)
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
                            FileTranscriptionState.Reason.TooLong -> R.string.transcribe_file_too_long
                        },
                        FileTranscriber.MAX_SECONDS / 60,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WorkingStatus(progress: FileTranscriber.Progress?) {
    val (label, fraction) = when (progress) {
        null -> stringResource(R.string.transcribe_file_starting) to null
        is FileTranscriber.Progress.Decoding -> stringResource(R.string.transcribe_file_decoding) to progress.fraction
        is FileTranscriber.Progress.Transcribing -> stringResource(
            R.string.transcribe_file_transcribing,
            (progress.fraction * 100).roundToInt(),
        ) to progress.fraction
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Result(text: String, onClose: () -> Unit) {
    val context = LocalContext.current
    if (text.isEmpty()) {
        Text(stringResource(R.string.notification_nothing_heard))
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Transcription", text))
        }) { Text(stringResource(R.string.history_copy)) }
        OutlinedButton(onClick = {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(share, null))
        }) { Text(stringResource(R.string.transcribe_file_share)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.transcribe_file_done)) }
    }
}

private fun fileName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
