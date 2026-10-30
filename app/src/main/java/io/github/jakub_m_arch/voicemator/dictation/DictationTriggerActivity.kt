package io.github.jakub_m_arch.voicemator.dictation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.jakub_m_arch.voicemator.MainActivity

/**
 * Invisible activity launched from the Quick Settings tile, the home screen widget and the
 * launcher shortcut. It toggles dictation: starts it when idle and stops it while recording.
 *
 * Android only grants microphone access to foreground services started while the app is in the
 * foreground, so these entry points briefly bring this activity up to start [DictationService].
 */
class DictationTriggerActivity : Activity() {

    override fun onResume() {
        super.onResume()
        when (DictationStatus.state.value) {
            DictationState.Idle -> if (DictationRequirements.areMet(this)) {
                ContextCompat.startForegroundService(this, DictationService.startIntent(this))
            } else {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            DictationState.Recording -> startService(DictationService.stopIntent(this))
            DictationState.Transcribing -> Unit
        }
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, DictationTriggerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
