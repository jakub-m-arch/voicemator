package io.github.jakub_m_arch.voicemator.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationService
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationTileService
import io.github.jakub_m_arch.voicemator.history.HistorySettingsCard
import io.github.jakub_m_arch.voicemator.licenses.LicensesActivity
import io.github.jakub_m_arch.voicemator.model.ModelState
import io.github.jakub_m_arch.voicemator.model.ParakeetModel
import io.github.jakub_m_arch.voicemator.overlay.RecordingOverlay
import kotlin.math.roundToInt

@Composable
fun SetupScreen(contentPadding: PaddingValues, viewModel: SetupViewModel = viewModel()) {
    val context = LocalContext.current
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val dictationState by viewModel.dictationState.collectAsStateWithLifecycle()

    var hasMicrophone by remember { mutableStateOf(DictationRequirements.hasMicrophonePermission(context)) }
    var hasNotifications by remember { mutableStateOf(DictationRequirements.hasNotificationPermission(context)) }
    var isTileAdded by remember { mutableStateOf(DictationTileService.isAdded(context)) }
    var canShowOverlay by remember { mutableStateOf(RecordingOverlay.canShow(context)) }
    var hasAccessibility by remember { mutableStateOf(DictationAccessibilityService.isEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        hasMicrophone = DictationRequirements.hasMicrophonePermission(context)
        hasNotifications = DictationRequirements.hasNotificationPermission(context)
        isTileAdded = DictationTileService.isAdded(context)
        canShowOverlay = RecordingOverlay.canShow(context)
        hasAccessibility = DictationAccessibilityService.isEnabled(context)
        viewModel.refreshModelState()
        onPauseOrDispose {}
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicrophone = granted }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotifications = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

        SetupStep(
            title = stringResource(R.string.setup_microphone_title),
            body = stringResource(R.string.setup_microphone_body),
            isDone = hasMicrophone,
        ) {
            Button(onClick = { microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.button_grant))
            }
        }

        SetupStep(
            title = stringResource(R.string.setup_notifications_title),
            body = stringResource(R.string.setup_notifications_body),
            isDone = hasNotifications,
        ) {
            Button(onClick = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.button_grant))
            }
        }

        ModelStep(
            state = modelState,
            onDownload = viewModel::downloadModel,
            onCancel = viewModel::cancelDownload,
        )

        SetupStep(
            title = stringResource(R.string.setup_tile_title),
            body = stringResource(R.string.setup_tile_body),
            isDone = isTileAdded,
        ) {
            Button(onClick = { requestAddTile(context) { isTileAdded = true } }) {
                Text(stringResource(R.string.button_add_tile))
            }
        }

        SetupStep(
            title = stringResource(R.string.setup_overlay_title),
            body = stringResource(R.string.setup_overlay_body),
            isDone = canShowOverlay,
        ) {
            Button(onClick = { context.startActivity(overlaySettingsIntent(context)) }) {
                Text(stringResource(R.string.button_open_settings))
            }
        }

        SetupStep(
            title = stringResource(R.string.setup_accessibility_title),
            body = stringResource(R.string.setup_accessibility_body),
            isDone = hasAccessibility,
        ) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(stringResource(R.string.button_open_settings))
            }
        }
        if (hasAccessibility) KeyboardButtonCard()

        TryItStep(
            state = dictationState,
            isReady = hasMicrophone && modelState == ModelState.Installed,
            onStart = {
                ContextCompat.startForegroundService(context, DictationService.startIntent(context))
            },
            onStop = { context.startService(DictationService.stopIntent(context)) },
        )

        HistorySettingsCard()

        TextButton(onClick = { context.startActivity(Intent(context, LicensesActivity::class.java)) }) {
            Text(stringResource(R.string.licenses_title))
        }
    }
}

@Composable
private fun KeyboardButtonCard() {
    val settings = KeyboardButtonSettings.get(LocalContext.current)
    val isEnabled by settings.isEnabled.collectAsStateWithLifecycle()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.keyboard_button_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isEnabled, onCheckedChange = settings::setEnabled)
            }
            Text(
                stringResource(R.string.keyboard_button_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelStep(state: ModelState, onDownload: () -> Unit, onCancel: () -> Unit) {
    val sizeMegabytes = (ParakeetModel.totalSizeBytes / BYTES_PER_MEGABYTE).toInt()
    SetupStep(
        title = stringResource(R.string.setup_model_title),
        body = stringResource(R.string.setup_model_body, sizeMegabytes),
        isDone = state == ModelState.Installed,
    ) {
        when (state) {
            ModelState.NotInstalled -> Button(onClick = onDownload) {
                Text(stringResource(R.string.button_download))
            }
            ModelState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.setup_model_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onDownload) { Text(stringResource(R.string.button_retry)) }
            }
            is ModelState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.setup_model_downloading, (state.progress * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.button_cancel)) }
                }
            }
            ModelState.Installed -> Unit
        }
    }
}

@Composable
private fun TryItStep(
    state: DictationState,
    isReady: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    SetupStep(
        title = stringResource(R.string.setup_try_title),
        body = stringResource(R.string.setup_try_body),
        isDone = false,
    ) {
        when (state) {
            DictationState.Idle -> Button(onClick = onStart, enabled = isReady) {
                Text(stringResource(R.string.button_start))
            }
            DictationState.Recording -> Button(onClick = onStop) {
                Text(stringResource(R.string.button_stop))
            }
            DictationState.Transcribing -> Text(
                stringResource(R.string.status_transcribing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SetupStep(
    title: String,
    body: String,
    isDone: Boolean,
    action: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isDone) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "✓ " + stringResource(R.string.status_done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!isDone) action()
        }
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

private fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

private const val BYTES_PER_MEGABYTE = 1_000_000L
