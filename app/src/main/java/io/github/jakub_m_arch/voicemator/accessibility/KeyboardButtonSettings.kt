package io.github.jakub_m_arch.voicemator.accessibility

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the keyboard mic button is shown, and where the user has dragged it. */
class KeyboardButtonSettings private constructor(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableIsEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))

    val isEnabled: StateFlow<Boolean> = mutableIsEnabled.asStateFlow()

    /** Distance from the right edge of the screen, in pixels, or null for the default. */
    val offsetFromRight: Int? get() = preferences.getInt(KEY_FROM_RIGHT, NOT_SET).takeIf { it != NOT_SET }

    /** Distance below the top edge of the keyboard (negative is above), or null for the default. */
    val offsetFromKeyboardTop: Int? get() = preferences.getInt(KEY_FROM_TOP, NOT_SET).takeIf { it != NOT_SET }

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        mutableIsEnabled.value = enabled
    }

    fun savePosition(offsetFromRight: Int, offsetFromKeyboardTop: Int) {
        preferences.edit {
            putInt(KEY_FROM_RIGHT, offsetFromRight)
            putInt(KEY_FROM_TOP, offsetFromKeyboardTop)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "keyboard_button"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FROM_RIGHT = "offset_from_right"
        private const val KEY_FROM_TOP = "offset_from_keyboard_top"
        private const val NOT_SET = Int.MIN_VALUE

        @Volatile
        private var instance: KeyboardButtonSettings? = null

        fun get(context: Context): KeyboardButtonSettings =
            instance ?: synchronized(this) {
                instance ?: KeyboardButtonSettings(context.applicationContext).also { instance = it }
            }
    }
}
