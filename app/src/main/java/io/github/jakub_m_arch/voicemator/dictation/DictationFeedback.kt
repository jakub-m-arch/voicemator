package io.github.jakub_m_arch.voicemator.dictation

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which signals mark the start and end of a recording. */
class FeedbackSettings private constructor(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableVibrate = MutableStateFlow(preferences.getBoolean(KEY_VIBRATE, true))
    private val mutableSound = MutableStateFlow(preferences.getBoolean(KEY_SOUND, false))

    /** A short vibration when recording starts and a double one when it stops. */
    val vibrate: StateFlow<Boolean> = mutableVibrate.asStateFlow()

    /** A short beep when recording starts and stops; silent while the phone is on silent. */
    val sound: StateFlow<Boolean> = mutableSound.asStateFlow()

    fun setVibrate(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VIBRATE, enabled) }
        mutableVibrate.value = enabled
    }

    fun setSound(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SOUND, enabled) }
        mutableSound.value = enabled
    }

    companion object {
        private const val PREFERENCES_NAME = "feedback"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SOUND = "sound"

        @Volatile
        private var instance: FeedbackSettings? = null

        fun get(context: Context): FeedbackSettings =
            instance ?: synchronized(this) {
                instance ?: FeedbackSettings(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Signals that recording started or stopped, so the user knows without looking at the screen.
 * The recording pill is optional and easy to miss, especially when dictating from the tile.
 */
class DictationFeedback(context: Context) {

    private val settings = FeedbackSettings.get(context)
    private val vibrator = context.getSystemService<VibratorManager>()?.defaultVibrator
    private val handler = Handler(Looper.getMainLooper())

    fun started() = signal(VibrationEffect.EFFECT_CLICK, ToneGenerator.TONE_PROP_BEEP)

    fun stopped() = signal(VibrationEffect.EFFECT_DOUBLE_CLICK, ToneGenerator.TONE_PROP_ACK)

    private fun signal(vibration: Int, tone: Int) {
        // Accessibility usage, unlike the default touch usage, still vibrates with touch feedback off.
        if (settings.vibrate.value) vibrator?.vibrate(VibrationEffect.createPredefined(vibration), VIBRATION_ATTRIBUTES)
        if (settings.sound.value) playTone(tone)
    }

    private fun playTone(tone: Int) {
        // The notification stream follows the ringer, so the beep stays quiet on a silent phone.
        val generator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No tone generator", e)
            return
        }
        generator.startTone(tone, TONE_MILLIS)
        handler.postDelayed(generator::release, TONE_MILLIS + RELEASE_DELAY_MILLIS)
    }

    private companion object {
        const val TAG = "DictationFeedback"
        const val TONE_VOLUME = 60
        const val TONE_MILLIS = 120
        const val RELEASE_DELAY_MILLIS = 200L
        val VIBRATION_ATTRIBUTES: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
    }
}
