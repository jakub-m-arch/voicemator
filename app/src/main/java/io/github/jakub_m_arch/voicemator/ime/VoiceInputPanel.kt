package io.github.jakub_m_arch.voicemator.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import kotlin.math.roundToInt

/**
 * The voice input panel: a status line with the elapsed time, a live waveform, a large button to
 * stop and a button to cancel. Colours follow the system's Material You palette.
 */
@SuppressLint("ViewConstructor")
class VoiceInputPanel(
    context: Context,
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
    private val onOpenApp: () -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val isNight =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val backgroundColor = systemColor(if (isNight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
    private val textColor = systemColor(if (isNight) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900)
    private val accentColor = systemColor(if (isNight) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)

    private val status = TextView(context).apply {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, STATUS_TEXT_SP)
        gravity = Gravity.CENTER
    }
    private val waveform = WaveformView(context)
    private val actionButton = ActionButton(context).apply {
        contentDescription = context.getString(R.string.voice_input_stop)
        setOnClickListener { onStop() }
    }
    private val cancelButton = TextView(context).apply {
        text = context.getString(R.string.voice_input_cancel)
        setTextColor(accentColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, BUTTON_TEXT_SP)
        val padding = dp(16)
        setPadding(padding, padding, padding, padding)
        isClickable = true
        isFocusable = true
        setOnClickListener { onCancel() }
    }

    private var recordingStartedAt = 0L
    private val clockTick = object : Runnable {
        override fun run() {
            updateStatusText()
            if (actionButton.state == DictationState.Recording) postDelayed(this, CLOCK_TICK_MILLIS)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)
        setPadding(dp(16), dp(16), dp(16), dp(12))
        // Input methods are drawn edge to edge; keep the controls above the navigation bar.
        setOnApplyWindowInsetsListener { view, insets ->
            val navigationBar = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            view.setPadding(dp(16), dp(16), dp(16), dp(12) + navigationBar)
            insets
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(waveform, LayoutParams(LayoutParams.MATCH_PARENT, dp(WAVEFORM_HEIGHT_DP)))
        val controls = FrameLayout(context).apply {
            addView(cancelButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ))
            addView(actionButton, FrameLayout.LayoutParams(dp(ACTION_SIZE_DP), dp(ACTION_SIZE_DP), Gravity.CENTER))
        }
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(ACTION_SIZE_DP + 8)))
        showState(DictationState.Idle)
    }

    fun showState(state: DictationState) {
        if (state != DictationState.Recording) waveform.clear()
        if (state == DictationState.Recording && actionButton.state != DictationState.Recording) {
            recordingStartedAt = SystemClock.elapsedRealtime()
        }
        actionButton.state = state
        actionButton.isEnabled = state == DictationState.Recording
        actionButton.visibility = VISIBLE
        waveform.isTranscribing = state == DictationState.Transcribing
        status.setOnClickListener(null)
        removeCallbacks(clockTick)
        clockTick.run()
    }

    fun pushLevel(level: Float) = waveform.push(level)

    fun showSetupNeeded() {
        status.text = context.getString(R.string.voice_input_setup_needed)
        status.setOnClickListener { onOpenApp() }
        actionButton.visibility = INVISIBLE
    }

    private fun updateStatusText() {
        status.text = when (actionButton.state) {
            DictationState.Idle -> context.getString(R.string.voice_input_starting)
            DictationState.Recording -> {
                val seconds = (SystemClock.elapsedRealtime() - recordingStartedAt) / 1000
                context.getString(R.string.voice_input_listening, seconds / 60, seconds % 60)
            }
            DictationState.Transcribing -> context.getString(R.string.voice_input_transcribing)
        }
    }

    private fun systemColor(id: Int) = ContextCompat.getColor(context, id)

    private fun dp(value: Int) = (value * density).roundToInt()

    /** Bars for the most recent loudness values, newest on the right; a travelling wave while transcribing. */
    private inner class WaveformView(context: Context) : View(context) {
        private val levels = FloatArray(BAR_COUNT)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 4 * density
        }
        var isTranscribing = false
            set(value) {
                field = value
                invalidate()
            }

        fun clear() {
            levels.fill(0f)
            invalidate()
        }

        fun push(level: Float) {
            levels.copyInto(levels, destinationOffset = 0, startIndex = 1)
            levels[BAR_COUNT - 1] = level
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val spacing = width / BAR_COUNT.toFloat()
            val centerY = height / 2f
            val minHeight = 4 * density
            for (i in 0 until BAR_COUNT) {
                val amount = if (isTranscribing) {
                    val t = SystemClock.uptimeMillis() / WAVE_PERIOD_MILLIS + i / 6.0
                    (0.15 + 0.15 * kotlin.math.sin(2 * Math.PI * t)).toFloat()
                } else {
                    levels[i]
                }
                val half = (minHeight + (height * 0.9f - minHeight) * amount) / 2
                val x = spacing * (i + 0.5f)
                canvas.drawLine(x, centerY - half, x, centerY + half, paint)
            }
            if (isTranscribing) postInvalidateOnAnimation()
        }
    }

    /** Round button: a stop square while recording, a spinner while transcribing. */
    private inner class ActionButton(context: Context) : View(context) {
        var state = DictationState.Idle
            set(value) {
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = RectF()

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            val radius = width / 2f
            paint.style = Paint.Style.FILL
            paint.color = if (state == DictationState.Recording) RECORDING_COLOR else accentColor
            canvas.drawCircle(radius, radius, radius, paint)
            paint.color = ON_ACCENT_COLOR
            when (state) {
                DictationState.Recording -> {
                    val half = radius * STOP_SQUARE_RATIO
                    val corner = 3 * density
                    canvas.drawRoundRect(radius - half, radius - half, radius + half, radius + half, corner, corner, paint)
                }
                DictationState.Transcribing, DictationState.Idle -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3 * density
                    paint.strokeCap = Paint.Cap.ROUND
                    val inset = radius * SPINNER_INSET_RATIO
                    arc.set(inset, inset, width - inset, height - inset)
                    val start = (SystemClock.uptimeMillis() % SPIN_MILLIS) * 360f / SPIN_MILLIS
                    canvas.drawArc(arc, start, SPINNER_SWEEP, false, paint)
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 250L
        const val STATUS_TEXT_SP = 16f
        const val BUTTON_TEXT_SP = 15f
        const val WAVEFORM_HEIGHT_DP = 64
        const val ACTION_SIZE_DP = 64
        const val BAR_COUNT = 32
        const val WAVE_PERIOD_MILLIS = 900.0
        const val SPIN_MILLIS = 900L
        const val SPINNER_SWEEP = 270f
        const val SPINNER_INSET_RATIO = 0.35f
        const val STOP_SQUARE_RATIO = 0.32f
        const val RECORDING_COLOR = 0xFFE5372E.toInt()
        const val ON_ACCENT_COLOR = 0xFFFFFFFF.toInt()
    }
}
