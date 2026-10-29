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
import io.github.jakub_m_arch.voicemator.asr.SpeechTranscriber
import io.github.jakub_m_arch.voicemator.audio.AudioRecorder
import io.github.jakub_m_arch.voicemator.model.ModelStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that records speech, transcribes it on-device and copies the text to the
 * clipboard.
 *
 * The model is loaded in parallel with recording, so its loading time is mostly hidden behind
 * the time the user spends speaking. It is released when the service stops to free memory.
 */
class DictationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = AudioRecorder(SpeechTranscriber.SAMPLE_RATE)
    private lateinit var notifications: DictationNotifications

    private var transcriber: Deferred<SpeechTranscriber>? = null
    private var loadedTranscriber: SpeechTranscriber? = null

    override fun onCreate() {
        super.onCreate()
        notifications = DictationNotifications(this).apply { createChannels() }
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
        loadedTranscriber?.close()
        DictationStatus.update(DictationState.Idle)
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

        try {
            recorder.start(scope)
        } catch (e: Exception) {
            finishWith { notifications.showFailure(e) }
            return
        }
        DictationStatus.update(DictationState.Recording)

        if (transcriber == null) {
            transcriber = scope.async(Dispatchers.Default) {
                SpeechTranscriber.load(ModelStorage(this@DictationService))
                    .also { loadedTranscriber = it }
            }
        }
    }

    private fun stopRecording() {
        if (DictationStatus.state.value != DictationState.Recording) return
        DictationStatus.update(DictationState.Transcribing)
        notifications.update(notifications.transcribing())

        scope.launch {
            try {
                val samples = recorder.stop()
                val text = if (samples.size < MIN_SAMPLES) {
                    ""
                } else {
                    val transcriber = checkNotNull(transcriber).await()
                    withContext(Dispatchers.Default) {
                        transcriber.transcribe(samples, SpeechTranscriber.SAMPLE_RATE)
                    }
                }
                finishWith {
                    if (text.isEmpty()) {
                        notifications.showNothingHeard()
                    } else {
                        copyToClipboard(text)
                        notifications.showCopied(text)
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
            finishWith {}
        }
    }

    private inline fun finishWith(report: () -> Unit) {
        DictationStatus.update(DictationState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        report()
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

        /** Recordings shorter than this are treated as accidental taps. */
        private const val MIN_SAMPLES = SpeechTranscriber.SAMPLE_RATE / 4

        fun startIntent(context: Context) = intent(context, ACTION_START)

        fun stopIntent(context: Context) = intent(context, ACTION_STOP)

        fun cancelIntent(context: Context) = intent(context, ACTION_CANCEL)

        private fun intent(context: Context, action: String) =
            Intent(context, DictationService::class.java).setAction(action)
    }
}
