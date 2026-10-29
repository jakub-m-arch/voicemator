package io.github.jakub_m_arch.voicemator.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.jakub_m_arch.voicemator.R
import io.github.jakub_m_arch.voicemator.dictation.DictationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A small mic button shown at the keyboard while it is open, and kept on screen while dictating
 * so that it can stop dictation too. Tap to toggle dictation; drag to move it.
 *
 * It is an accessibility overlay, so it needs no "display over other apps" permission, and it
 * never takes focus, so the text field keeps the keyboard and the cursor.
 */
class KeyboardMicButton(
    private val service: AccessibilityService,
    private val settings: KeyboardButtonSettings,
    private val onTap: () -> Unit,
) {
    private val windowManager = checkNotNull(service.getSystemService<WindowManager>())
    private val density = service.resources.displayMetrics.density
    private val size = (SIZE_DP * density).roundToInt()
    private val margin = (MARGIN_DP * density).roundToInt()
    private val view = MicButtonView(service).apply {
        contentDescription = service.getString(R.string.keyboard_button_description)
        setOnTouchListener(DragOrTapListener())
    }
    private val params = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = WINDOW_TITLE
    }

    private var keyboardTop: Int? = null
    private var dictationState = DictationState.Idle
    private var isEnabled = true

    /** Called with the keyboard's bounds on screen, or null when no keyboard is shown. */
    fun onKeyboardChanged(bounds: Rect?) {
        val top = bounds?.top
        if (top == keyboardTop) return
        keyboardTop = top
        refresh()
    }

    fun onDictationStateChanged(state: DictationState) {
        dictationState = state
        view.state = state
        refresh()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        refresh()
    }

    fun destroy() {
        if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
    }

    private fun refresh() {
        val keyboardTop = keyboardTop
        val shouldShow = isEnabled && (keyboardTop != null || dictationState != DictationState.Idle)
        if (!shouldShow) {
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            return
        }
        // Follow the keyboard; while it is hidden during dictation, stay where the button was.
        if (keyboardTop != null) {
            val screenWidth = windowManager.currentWindowMetrics.bounds.width()
            params.x = screenWidth - size - (settings.offsetFromRight ?: margin)
            // By default the button sits in the keyboard's top row, where keyboards put their own mic.
            params.y = keyboardTop + (settings.offsetFromKeyboardTop ?: (margin / 4))
        }
        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, params)
        } else {
            windowManager.addView(view, params)
        }
    }

    private inner class DragOrTapListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility") // Taps are forwarded to performClick().
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) isDragging = true
                    if (isDragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (isDragging) savePosition() else view.performClick()
            }
            return true
        }

        private fun savePosition() {
            val keyboardTop = keyboardTop ?: return
            val screenWidth = windowManager.currentWindowMetrics.bounds.width()
            settings.savePosition(
                offsetFromRight = screenWidth - size - params.x,
                offsetFromKeyboardTop = params.y - keyboardTop,
            )
        }
    }

    private inner class MicButtonView(context: Context) : View(context) {
        var state = DictationState.Idle
            set(value) {
                field = value
                invalidate()
            }

        private val background = Paint(Paint.ANTI_ALIAS_FLAG)
        private val icon = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_dictate)).mutate()

        init {
            setOnClickListener { onTap() }
            elevation = ELEVATION_DP * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val radius = width / 2f
            background.color = when (state) {
                DictationState.Idle -> IDLE_COLOR
                DictationState.Recording -> RECORDING_COLOR
                DictationState.Transcribing -> TRANSCRIBING_COLOR
            }
            if (state == DictationState.Recording) {
                // Breathe gently so it is obvious the microphone is on.
                val t = (SystemClock.uptimeMillis() % PULSE_MILLIS) / PULSE_MILLIS.toFloat()
                background.alpha = (PULSE_MIN_ALPHA + (255 - PULSE_MIN_ALPHA) * (sin(2 * Math.PI * t) + 1) / 2).toInt()
                postInvalidateOnAnimation()
            }
            canvas.drawCircle(radius, radius, radius, background)

            val inset = (width * ICON_INSET_RATIO).roundToInt()
            icon.setBounds(inset, inset, width - inset, height - inset)
            icon.draw(canvas)
        }
    }

    private companion object {
        const val WINDOW_TITLE = "Voicemator keyboard button"
        const val SIZE_DP = 40
        const val MARGIN_DP = 8
        const val ELEVATION_DP = 6
        const val ICON_INSET_RATIO = 0.24f
        const val PULSE_MILLIS = 1200L
        const val PULSE_MIN_ALPHA = 170
        const val IDLE_COLOR = 0xF0242424.toInt()
        const val RECORDING_COLOR = 0xFFE5372E.toInt()
        const val TRANSCRIBING_COLOR = 0xFF7C3AED.toInt()
    }
}
