package io.github.jakub_m_arch.voicemator.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import io.github.jakub_m_arch.voicemator.MainActivity
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationService
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus

/**
 * Optional accessibility service that adds two things:
 *
 * - the system accessibility button (a floating button or a navigation bar button, as the user
 *   chooses) toggles dictation;
 * - finished transcriptions are pasted into the focused text field instead of only being copied.
 *
 * It reads window content only to find the focused field, and only when inserting text.
 */
class DictationAccessibilityService : AccessibilityService() {

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) = toggleDictation()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun toggleDictation() {
        when (DictationStatus.state.value) {
            DictationState.Idle -> if (DictationRequirements.areMet(this)) {
                ContextCompat.startForegroundService(this, DictationService.startIntent(this))
            } else {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            DictationState.Recording -> startService(DictationService.stopIntent(this))
            DictationState.Transcribing -> Unit
        }
    }

    /** Pastes the clipboard into the focused editable field. Returns false if there is none. */
    private fun pasteIntoFocusedField(): Boolean {
        val field = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!field.isEditable || field.isPassword) return false
        return field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    companion object {
        @Volatile
        private var instance: DictationAccessibilityService? = null

        /**
         * Pastes the current clipboard into the focused text field of whatever app is in front.
         * Returns false when the service is off or no editable field has focus.
         */
        fun pasteIntoFocusedField(): Boolean = instance?.pasteIntoFocusedField() ?: false

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, DictationAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
