package io.github.jakub_m_arch.voicemator.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import io.github.jakub_m_arch.voicemator.MainActivity
import io.github.jakub_m_arch.voicemator.dictation.DictationRequirements
import io.github.jakub_m_arch.voicemator.dictation.DictationService
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import io.github.jakub_m_arch.voicemator.dictation.DictationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional accessibility service that adds:
 *
 * - a mic button at the keyboard whenever one is open ([KeyboardMicButton]);
 * - the system accessibility shortcut (a floating button, the navigation bar button or holding
 *   both volume keys, as the user chooses) to toggle dictation;
 * - pasting finished transcriptions into the focused text field instead of only copying them.
 *
 * It watches window changes only to know whether a keyboard is open, and reads window content
 * only to find the focused field: when inserting text, and to tell which app a dictation is for,
 * so that app's profile applies.
 */
class DictationAccessibilityService : AccessibilityService() {

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) = toggleDictation()
    }

    private var scope: CoroutineScope? = null
    private var keyboardButton: KeyboardMicButton? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
        val settings = KeyboardButtonSettings.get(this)
        val button = KeyboardMicButton(this, settings, onTap = ::toggleDictation)
        keyboardButton = button
        scope = MainScope().apply {
            launch { DictationStatus.state.collect(button::onDictationStateChanged) }
            launch { settings.isEnabled.collect(button::setEnabled) }
        }
        button.onKeyboardChanged(keyboardBounds())
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        scope?.cancel()
        scope = null
        keyboardButton?.destroy()
        keyboardButton = null
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            keyboardButton?.onKeyboardChanged(keyboardBounds())
        }
    }

    /** Bounds of the on-screen keyboard, or null when none is shown. */
    private fun keyboardBounds(): Rect? = windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { window -> Rect().also(window::getBoundsInScreen) }
        ?.takeUnless { it.isEmpty }

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

    /** Package name of the app whose editable field has focus, or null if there is none. */
    private fun focusedApp(): String? {
        val field = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return field.packageName?.toString()?.takeIf { field.isEditable }
    }

    companion object {
        @Volatile
        private var instance: DictationAccessibilityService? = null

        /** The app with a focused text field, as far as the service can tell; null when it is off. */
        fun focusedApp(): String? = instance?.focusedApp()

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
