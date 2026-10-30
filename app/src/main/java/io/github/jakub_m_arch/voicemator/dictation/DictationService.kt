package io.github.jakub_m_arch.voicemator.dictation

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import io.github.jakub_m_arch.voicemator.accessibility.DictationAccessibilityService
import io.github.jakub_m_arch.voicemator.overlay.RecordingOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that records speech, transcribes it on-device and copies the text to the
 * clipboard. When [DictationAccessibilityService] is on, the text is also pasted into the focused
 * text field.
 *
 * The work itself is a [DictationSession]; this service keeps it alive in the background and
 * shows the notification and the recording pill.
 */
class DictationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: DictationNotifications
    private lateinit var overlay: RecordingOverlay
    private lateinit var feedback: DictationFeedback
    private var session: DictationSession? = null
    private var levelUpdates: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = DictationNotifications(this).apply { createChannels() }
        overlay = RecordingOverlay(this, onTap = ::stopRecording)
        feedback = DictationFeedback(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_CANCEL -> cancelRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        session?.close()
        if (session != null) DictationStatus.publish(this, DictationState.Idle)
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // Checked by DictationRequirements before starting.
    private fun startRecording() {
        ServiceCompat.startForeground(
            this,
            DictationNotifications.ONGOING_NOTIFICATION_ID,
            notifications.recording(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (DictationStatus.state.value != DictationState.Idle) return

        val session = DictationSession(this, scope, targetApp = DictationAccessibilityService::focusedApp)
        this.session = session
        try {
            session.start()
        } catch (e: Exception) {
            finishWith { notifications.showFailure(e) }
            return
        }
        DictationStatus.publish(this, DictationState.Recording)
        feedback.started()
        overlay.show()
        levelUpdates = scope.launch {
            launch { session.level.collect(overlay::pushLevel) }
            launch { session.preview.collect(overlay::showPreview) }
        }
    }

    private fun stopRecording() {
        val session = session ?: return
        if (DictationStatus.state.value != DictationState.Recording) return
        DictationStatus.publish(this, DictationState.Transcribing)
        feedback.stopped()
        notifications.update(notifications.transcribing())
        levelUpdates?.cancel()
        overlay.showTranscribing()

        scope.launch {
            try {
                val text = session.finish()
                if (text.isEmpty()) {
                    finishWith { notifications.showNothingHeard() }
                } else {
                    copyToClipboard(text)
                    val inserted = DictationAccessibilityService.pasteIntoFocusedField()
                    finishWith {
                        overlay.finishWithSuccess()
                        notifications.showResult(text, inserted)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dictation failed", e)
                finishWith { notifications.showFailure(e) }
            }
        }
    }

    private fun cancelRecording() {
        val session = session ?: return
        if (DictationStatus.state.value != DictationState.Recording) return
        feedback.stopped()
        scope.launch {
            session.cancel()
            finishWith {}
        }
    }

    private inline fun finishWith(report: () -> Unit) {
        levelUpdates?.cancel()
        session?.close()
        session = null
        DictationStatus.publish(this, DictationState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        report()
        overlay.hide()
        stopSelf()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = checkNotNull(getSystemService<ClipboardManager>())
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    companion object {
        private const val TAG = "DictationService"
        private const val ACTION_START = "io.github.jakub_m_arch.voicemator.action.START"
        private const val ACTION_STOP = "io.github.jakub_m_arch.voicemator.action.STOP"
        private const val ACTION_CANCEL = "io.github.jakub_m_arch.voicemator.action.CANCEL"
        private const val CLIP_LABEL = "Transcription"

        fun startIntent(context: Context) = intent(context, ACTION_START)

        fun stopIntent(context: Context) = intent(context, ACTION_STOP)

        fun cancelIntent(context: Context) = intent(context, ACTION_CANCEL)

        private fun intent(context: Context, action: String) =
            Intent(context, DictationService::class.java).setAction(action)
    }
}
