package io.github.jakub_m_arch.voicemator.dictation

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import io.github.jakub_m_arch.voicemator.accessibility.DictationAccessibilityService
import io.github.jakub_m_arch.voicemator.asr.ChunkedTranscription
import io.github.jakub_m_arch.voicemator.asr.SpeechTranscriber
import io.github.jakub_m_arch.voicemator.audio.AudioRecorder
import io.github.jakub_m_arch.voicemator.history.HistoryRepository
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import io.github.jakub_m_arch.voicemator.overlay.RecordingOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that records speech, transcribes it on-device and copies the text to the
 * clipboard. When [DictationAccessibilityService] is on, the text is also pasted into the focused
 * text field.
 *
 * The model is loaded in parallel with recording, and longer recordings are transcribed in
 * chunks while the user is still speaking, so the text is ready soon after dictation stops. The
 * model is released when the service stops to free memory.
 */
class DictationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = AudioRecorder(SpeechTranscriber.SAMPLE_RATE)
    private lateinit var notifications: DictationNotifications
    private lateinit var overlay: RecordingOverlay
    private var levelUpdates: Job? = null

    private var transcriber: Deferred<SpeechTranscriber>? = null
    private var transcription: ChunkedTranscription? = null
    private var loadedTranscriber: SpeechTranscriber? = null

    override fun onCreate() {
        super.onCreate()
        notifications = DictationNotifications(this).apply { createChannels() }
        overlay = RecordingOverlay(this, onTap = ::stopRecording)
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
        loadedTranscriber?.close()
        updateState(DictationState.Idle)
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

        val transcriber = transcriber ?: scope.async(Dispatchers.Default) {
            SpeechTranscriber.load(ModelStorage(this@DictationService))
                .also { loadedTranscriber = it }
        }.also { transcriber = it }
        val transcription =
            ChunkedTranscription(scope, transcriber, SpeechTranscriber.SAMPLE_RATE, MIN_SAMPLES)

        try {
            recorder.start(scope, transcription::append)
        } catch (e: Exception) {
            transcription.cancel()
            finishWith { notifications.showFailure(e) }
            return
        }
        this.transcription = transcription
        updateState(DictationState.Recording)
        overlay.show()
        levelUpdates = scope.launch { recorder.level.collect(overlay::pushLevel) }
    }

    private fun stopRecording() {
        if (DictationStatus.state.value != DictationState.Recording) return
        updateState(DictationState.Transcribing)
        notifications.update(notifications.transcribing())
        levelUpdates?.cancel()
        overlay.showTranscribing()

        scope.launch {
            try {
                recorder.stop()
                val transcription = checkNotNull(transcription)
                val text = transcription.finish()
                if (text.isEmpty()) {
                    finishWith { notifications.showNothingHeard() }
                } else {
                    copyToClipboard(text)
                    val inserted = DictationAccessibilityService.pasteIntoFocusedField()
                    saveToHistory(text, transcription.recordedMillis)
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
        if (DictationStatus.state.value != DictationState.Recording) return
        scope.launch {
            runCatching { recorder.stop() }
            transcription?.cancel()
            finishWith {}
        }
    }

    private inline fun finishWith(report: () -> Unit) {
        levelUpdates?.cancel()
        transcription = null
        updateState(DictationState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        report()
        overlay.hide()
        stopSelf()
    }

    private suspend fun saveToHistory(text: String, audioDurationMillis: Long) {
        try {
            val history = HistoryRepository.get(this)
            withContext(Dispatchers.IO) { history.record(text, audioDurationMillis) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The text is already on the clipboard; a history failure must not lose it.
            Log.e(TAG, "Saving to history failed", e)
        }
    }

    /** Publishes [state] and asks the system to redraw the tile, which it does not do on its own. */
    private fun updateState(state: DictationState) {
        DictationStatus.update(state)
        TileService.requestListeningState(this, ComponentName(this, DictationTileService::class.java))
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

        /** Recordings shorter than this are treated as accidental taps. */
        private const val MIN_SAMPLES = SpeechTranscriber.SAMPLE_RATE / 4

        fun startIntent(context: Context) = intent(context, ACTION_START)

        fun stopIntent(context: Context) = intent(context, ACTION_STOP)

        fun cancelIntent(context: Context) = intent(context, ACTION_CANCEL)

        private fun intent(context: Context, action: String) =
            Intent(context, DictationService::class.java).setAction(action)
    }
}
