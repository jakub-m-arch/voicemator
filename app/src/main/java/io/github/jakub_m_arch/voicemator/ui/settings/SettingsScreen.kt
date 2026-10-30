package io.github.jakub_m_arch.voicemator.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.accessibility.DictationAccessibilityService
import io.github.jakub_m_arch.voicemator.accessibility.KeyboardButtonSettings
import io.github.jakub_m_arch.voicemator.asr.ChunkedTranscription
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationTileService
import io.github.jakub_m_arch.voicemator.dictation.FeedbackSettings
import io.github.jakub_m_arch.voicemator.dictation.TranscriptionSettings
import io.github.jakub_m_arch.voicemator.files.TranscribeFileActivity
import io.github.jakub_m_arch.voicemator.history.HistoryDatabase
import io.github.jakub_m_arch.voicemator.history.HistoryMode
import io.github.jakub_m_arch.voicemator.history.HistoryViewModel
import io.github.jakub_m_arch.voicemator.ime.VoiceInputMethodService
import io.github.jakub_m_arch.voicemator.licenses.LicensesActivity
import io.github.jakub_m_arch.voicemator.model.DownloadSettings
import io.github.jakub_m_arch.voicemator.model.ModelState
import io.github.jakub_m_arch.voicemator.model.ParakeetModel
import io.github.jakub_m_arch.voicemator.overlay.RecordingOverlay
import io.github.jakub_m_arch.voicemator.profiles.Profile
import io.github.jakub_m_arch.voicemator.profiles.ProfilesActivity
import io.github.jakub_m_arch.voicemator.text.DictionaryActivity
import io.github.jakub_m_arch.voicemator.text.DictionaryEntry
import kotlin.math.roundToInt

/** Which explanation dialog is open, if any. */
private enum class InfoTopic { VoiceInput, KeyboardButton, Overlay }

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(SystemState.read(context)) }

    LifecycleResumeEffect(Unit) {
        permissions = SystemState.read(context)
        viewModel.refreshModelState()
        onPauseOrDispose {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        val isReady = permissions.hasMicrophone && permissions.hasNotifications && modelState == ModelState.Installed
        if (!isReady) {
            GettingStarted(
                state = permissions,
                modelState = modelState,
                onMicrophoneGranted = { permissions = permissions.copy(hasMicrophone = it) },
                onNotificationsGranted = { permissions = permissions.copy(hasNotifications = it) },
                onDownload = viewModel::downloadModel,
                onCancelDownload = viewModel::cancelDownload,
            )
        }

        WaysToDictate(permissions, onTileAdded = { permissions = permissions.copy(isTileAdded = true) })
        Signals()
        Transcription()
        SpeechModel(viewModel, modelState)
        HistoryAndPrivacy(historyViewModel)
        About()
    }
}

/** Permissions and system settings that the screen reflects, re-read whenever it resumes. */
private data class SystemState(
    val hasMicrophone: Boolean,
    val hasNotifications: Boolean,
    val isTileAdded: Boolean,
    val canShowOverlay: Boolean,
    val hasAccessibility: Boolean,
    val hasVoiceInput: Boolean,
) {
    companion object {
        fun read(context: Context) = SystemState(
            hasMicrophone = DictationRequirements.hasMicrophonePermission(context),
            hasNotifications = DictationRequirements.hasNotificationPermission(context),
            isTileAdded = DictationTileService.isAdded(context),
            canShowOverlay = RecordingOverlay.canShow(context),
            hasAccessibility = DictationAccessibilityService.isEnabled(context),
            hasVoiceInput = isVoiceInputEnabled(context),
        )
    }
}

@Composable
private fun GettingStarted(
    state: SystemState,
    modelState: ModelState,
    onMicrophoneGranted: (Boolean) -> Unit,
    onNotificationsGranted: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onMicrophoneGranted)
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onNotificationsGranted)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.getting_started_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.getting_started_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            ChecklistRow(Icons.Outlined.Mic, stringResource(R.string.setup_microphone_title), stringResource(R.string.setup_microphone_body), state.hasMicrophone) {
                Button(onClick = { microphone.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.button_grant)) }
            }
            ChecklistRow(Icons.Outlined.Notifications, stringResource(R.string.setup_notifications_title), stringResource(R.string.setup_notifications_body), state.hasNotifications) {
                Button(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.button_grant)) }
            }
            val modelSummary = when (modelState) {
                is ModelState.Downloading, ModelState.Failed -> modelStatus(modelState)
                else -> stringResource(R.string.setup_model_body, MODEL_MEGABYTES)
            }
            ChecklistRow(Icons.Outlined.Download, stringResource(R.string.setup_model_title), modelSummary, modelState == ModelState.Installed) {
                when (modelState) {
                    is ModelState.Downloading -> OutlinedButton(onClick = onCancelDownload) { Text(stringResource(R.string.button_cancel)) }
                    ModelState.Failed -> Button(onClick = onDownload) { Text(stringResource(R.string.button_retry)) }
                    else -> Button(onClick = onDownload) { Text(stringResource(R.string.button_download)) }
                }
            }
            if (modelState is ModelState.Downloading) {
                LinearProgressIndicator(
                    progress = { modelState.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(icon: ImageVector, title: String, summary: String, isDone: Boolean, action: @Composable () -> Unit) {
    SettingsRow(
        icon = if (isDone) Icons.Outlined.CheckCircle else icon,
        title = title,
        summary = if (isDone) stringResource(R.string.status_done) else summary,
        trailing = if (isDone) null else action,
    )
}

@Composable
private fun WaysToDictate(state: SystemState, onTileAdded: () -> Unit) {
    val context = LocalContext.current
    val keyboardButton = KeyboardButtonSettings.get(context)
    val showKeyboardButton by keyboardButton.isEnabled.collectAsStateWithLifecycle()
    var openTopic by rememberSaveable { mutableStateOf<InfoTopic?>(null) }

    SettingsSection(stringResource(R.string.section_ways)) {
        SettingsRow(
            icon = Icons.Outlined.SpaceDashboard,
            title = stringResource(R.string.setup_tile_title),
            summary = stringResource(if (state.isTileAdded) R.string.summary_tile_added else R.string.summary_tile_add),
            onClick = if (state.isTileAdded) null else ({ requestAddTile(context, onTileAdded) }),
        )
        SettingsRow(
            icon = Icons.Outlined.Keyboard,
            title = stringResource(R.string.setup_voice_input_title),
            summary = stringResource(if (state.hasVoiceInput) R.string.summary_voice_input_on else R.string.summary_off),
            onClick = { openTopic = InfoTopic.VoiceInput },
        )
        if (state.hasAccessibility) {
            SettingsSwitchRow(
                icon = Icons.Outlined.KeyboardVoice,
                title = stringResource(R.string.setup_accessibility_title),
                summary = stringResource(R.string.keyboard_button_hint),
                checked = showKeyboardButton,
                onCheckedChange = keyboardButton::setEnabled,
            )
        } else {
            SettingsRow(
                icon = Icons.Outlined.Accessibility,
                title = stringResource(R.string.setup_accessibility_title),
                summary = stringResource(R.string.summary_needs_accessibility),
                onClick = { openTopic = InfoTopic.KeyboardButton },
            )
        }
        SettingsRow(
            icon = Icons.Outlined.Toll,
            title = stringResource(R.string.setup_overlay_title),
            summary = stringResource(if (state.canShowOverlay) R.string.summary_on else R.string.summary_needs_overlay),
            onClick = { openTopic = InfoTopic.Overlay },
        )
    }

    when (openTopic) {
        InfoTopic.VoiceInput -> InfoDialog(
            title = stringResource(R.string.setup_voice_input_title),
            body = stringResource(R.string.setup_voice_input_body),
            actionLabel = stringResource(R.string.button_open_settings),
            onAction = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            onDismiss = { openTopic = null },
        )
        InfoTopic.KeyboardButton -> InfoDialog(
            title = stringResource(R.string.setup_accessibility_title),
            body = stringResource(R.string.setup_accessibility_body),
            actionLabel = stringResource(R.string.button_open_settings),
            onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onDismiss = { openTopic = null },
        )
        InfoTopic.Overlay -> InfoDialog(
            title = stringResource(R.string.setup_overlay_title),
            body = stringResource(R.string.setup_overlay_body),
            actionLabel = stringResource(R.string.button_open_settings),
            onAction = { context.startActivity(overlaySettingsIntent(context)) },
            onDismiss = { openTopic = null },
        )
        null -> Unit
    }
}

@Composable
private fun Signals() {
    val settings = FeedbackSettings.get(LocalContext.current)
    val vibrate by settings.vibrate.collectAsStateWithLifecycle()
    val sound by settings.sound.collectAsStateWithLifecycle()

    SettingsSection(stringResource(R.string.section_signals)) {
        SettingsSwitchRow(
            icon = Icons.Outlined.Vibration,
            title = stringResource(R.string.row_vibrate_title),
            summary = stringResource(R.string.row_vibrate_summary),
            checked = vibrate,
            onCheckedChange = settings::setVibrate,
        )
        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = stringResource(R.string.row_sound_title),
            summary = stringResource(R.string.row_sound_summary),
            checked = sound,
            onCheckedChange = settings::setSound,
        )
    }
}

@Composable
private fun Transcription() {
    val context = LocalContext.current
    val settings = TranscriptionSettings.get(context)
    val removeFillers by settings.removeFillers.collectAsStateWithLifecycle()
    val finalPass by settings.finalPass.collectAsStateWithLifecycle()
    val dictionary by remember { HistoryDatabase.get(context).dictionary().observe(profileId = null) }
        .collectAsStateWithLifecycle(initialValue = emptyList<DictionaryEntry>())
    val profiles by remember { HistoryDatabase.get(context).profiles().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList<Profile>())
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.startActivity(TranscribeFileActivity.intent(context, uri))
    }

    SettingsSection(stringResource(R.string.transcription_settings_title)) {
        SettingsSwitchRow(
            icon = Icons.Outlined.CleaningServices,
            title = stringResource(R.string.row_fillers_title),
            summary = stringResource(R.string.row_fillers_summary),
            checked = removeFillers,
            onCheckedChange = settings::setRemoveFillers,
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Autorenew,
            title = stringResource(R.string.row_final_pass_title),
            summary = stringResource(R.string.row_final_pass_summary, ChunkedTranscription.MAX_FINAL_PASS_SECONDS / 60),
            checked = finalPass,
            onCheckedChange = settings::setFinalPass,
        )
        SettingsRow(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = stringResource(R.string.dictionary_title),
            summary = if (dictionary.isEmpty()) {
                stringResource(R.string.row_dictionary_empty)
            } else {
                pluralStringResource(R.plurals.row_dictionary_entries, dictionary.size, dictionary.size)
            },
            onClick = { context.startActivity(DictionaryActivity.intent(context)) },
        )
        SettingsRow(
            icon = Icons.Outlined.Apps,
            title = stringResource(R.string.profiles_title),
            summary = if (profiles.isEmpty()) {
                stringResource(R.string.row_profiles_empty)
            } else {
                pluralStringResource(R.plurals.row_profiles_count, profiles.size, profiles.size)
            },
            onClick = { context.startActivity(Intent(context, ProfilesActivity::class.java)) },
        )
        SettingsRow(
            icon = Icons.Outlined.AudioFile,
            title = stringResource(R.string.row_transcribe_file_title),
            summary = stringResource(R.string.row_transcribe_file_summary),
            onClick = { pickAudio.launch(arrayOf("audio/*", "application/ogg")) },
        )
    }
}

@Composable
private fun modelStatus(state: ModelState): String = when (state) {
    ModelState.Installed -> stringResource(R.string.model_installed, MODEL_MEGABYTES)
    ModelState.NotInstalled -> stringResource(R.string.model_not_installed)
    ModelState.Failed -> stringResource(R.string.setup_model_failed)
    is ModelState.Downloading -> if (state.isWaitingForNetwork) {
        val wifiOnly by DownloadSettings.get(LocalContext.current).wifiOnly.collectAsStateWithLifecycle()
        stringResource(
            if (wifiOnly) R.string.model_waiting_for_wifi else R.string.model_waiting_for_network,
            (state.progress * 100).roundToInt(),
        )
    } else {
        stringResource(R.string.setup_model_downloading, (state.progress * 100).roundToInt())
    }
}

@Composable
private fun SpeechModel(viewModel: SettingsViewModel, modelState: ModelState) {
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val dictationState by viewModel.dictationState.collectAsStateWithLifecycle()
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.setup_model_title)) {
        SettingsRow(
            icon = Icons.Outlined.Memory,
            title = stringResource(R.string.model_name),
            summary = modelStatus(modelState),
        )
        SettingsSwitchRow(
            icon = Icons.Outlined.Wifi,
            title = stringResource(R.string.row_wifi_only_title),
            summary = stringResource(R.string.row_wifi_only_summary),
            checked = wifiOnly,
            onCheckedChange = viewModel::setWifiOnly,
        )
        if (modelState == ModelState.Installed && dictationState == DictationState.Idle) {
            SettingsRow(
                icon = Icons.Outlined.Delete,
                title = stringResource(R.string.row_delete_model_title),
                summary = stringResource(R.string.row_delete_model_summary, MODEL_MEGABYTES),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { isConfirmingDelete = true },
                trailing = {},
            )
        }
    }

    if (isConfirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_model_confirm_title),
            body = stringResource(R.string.delete_model_confirm_body, MODEL_MEGABYTES),
            confirmLabel = stringResource(R.string.history_delete),
            onConfirm = viewModel::deleteModel,
            onDismiss = { isConfirmingDelete = false },
        )
    }
}

@Composable
private fun HistoryAndPrivacy(viewModel: HistoryViewModel) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val textCount by viewModel.textCount.collectAsStateWithLifecycle()
    var isChoosingMode by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableStateOf<HistoryMode?>(null) }
    var isConfirmingRemoveTexts by rememberSaveable { mutableStateOf(false) }
    var isConfirmingClear by rememberSaveable { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.section_history)) {
        SettingsRow(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.history_settings_title),
            summary = modeLabel(mode).first,
            onClick = { isChoosingMode = true },
        )
        if (textCount > 0) {
            SettingsRow(
                icon = Icons.Outlined.TextFields,
                title = stringResource(R.string.history_remove_texts),
                summary = stringResource(R.string.row_remove_texts_summary),
                onClick = { isConfirmingRemoveTexts = true },
            )
        }
        SettingsRow(
            icon = Icons.Outlined.DeleteForever,
            title = stringResource(R.string.history_clear),
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { isConfirmingClear = true },
            trailing = {},
        )
    }

    if (isChoosingMode) {
        ChoiceDialog(
            title = stringResource(R.string.history_settings_title),
            options = HistoryMode.entries,
            selected = mode,
            label = { modeLabel(it) },
            onSelect = { option ->
                // Leaving full history: offer to erase texts that are already saved.
                if (option != HistoryMode.Full && mode == HistoryMode.Full && textCount > 0) {
                    pendingMode = option
                } else {
                    viewModel.setMode(option)
                }
            },
            onDismiss = { isChoosingMode = false },
        )
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
private fun modeLabel(mode: HistoryMode): Pair<String, String> = when (mode) {
    HistoryMode.Full -> stringResource(R.string.history_mode_full) to stringResource(R.string.history_mode_full_body)
    HistoryMode.StatisticsOnly -> stringResource(R.string.history_mode_statistics) to stringResource(R.string.history_mode_statistics_body)
    HistoryMode.Off -> stringResource(R.string.history_mode_off) to stringResource(R.string.history_mode_off_body)
}

@Composable
private fun About() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sourceUrl = stringResource(R.string.licenses_app_url)
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    SettingsSection(stringResource(R.string.section_about)) {
        SettingsRow(
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.row_version_title, version),
            summary = stringResource(R.string.row_version_summary),
            onClick = { uriHandler.openUri(sourceUrl) },
        )
        SettingsRow(
            icon = Icons.Outlined.Gavel,
            title = stringResource(R.string.licenses_title),
            onClick = { context.startActivity(Intent(context, LicensesActivity::class.java)) },
        )
    }
}

private fun requestAddTile(context: Context, onAdded: () -> Unit) {
    val statusBarManager = context.getSystemService<StatusBarManager>() ?: return
    statusBarManager.requestAddTileService(
        ComponentName(context, DictationTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_dictate),
        ContextCompat.getMainExecutor(context),
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        ) {
            DictationTileService.setAdded(context, true)
            onAdded()
        }
    }
}

private fun isVoiceInputEnabled(context: Context): Boolean {
    val inputMethods = context.getSystemService<InputMethodManager>() ?: return false
    val component = ComponentName(context, VoiceInputMethodService::class.java)
    return inputMethods.enabledInputMethodList.any { it.component == component }
}

private fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

private const val BYTES_PER_MEGABYTE = 1_000_000L
private val MODEL_MEGABYTES = (ParakeetModel.totalSizeBytes / BYTES_PER_MEGABYTE).toInt()
