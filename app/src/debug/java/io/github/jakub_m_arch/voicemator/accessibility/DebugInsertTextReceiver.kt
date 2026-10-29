package io.github.jakub_m_arch.voicemator.accessibility

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService

/**
 * Debug-only stand-in for a finished dictation, for testing text insertion on an emulator:
 *
 * `adb shell am broadcast -n <package>/.accessibility.DebugInsertTextReceiver --es text "Hello"`
 */
class DebugInsertTextReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Transcription", text))
        val inserted = DictationAccessibilityService.pasteIntoFocusedField()
        Log.i(TAG, "Inserted into focused field: $inserted")
    }

    private companion object {
        const val TAG = "DebugInsertText"
        const val EXTRA_TEXT = "text"
    }
}
