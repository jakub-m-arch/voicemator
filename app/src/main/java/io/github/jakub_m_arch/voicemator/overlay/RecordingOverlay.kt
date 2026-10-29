package io.github.jakub_m_arch.voicemator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import kotlin.math.roundToInt

/**
 * Shows [RecordingPillView] at the top of the screen, wrapped around the camera cutout when the
 * device has one, with the live transcription in a caption below it. Requires the "Display over
 * other apps" permission; without it nothing is shown.
 */
class RecordingOverlay(context: Context, private val onTap: () -> Unit) {

    private val context = context.applicationContext
    private val windowManager = checkNotNull(context.getSystemService<WindowManager>())
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private var window: PillWindow? = null

    /** A pill still showing its result after dictation ended. */
    private var finishingWindow: PillWindow? = null

    fun show() {
        if (window != null || !canShow(context)) return
        finishingWindow?.let(::remove)
        val layout = computeLayout()
        val pillWindow = PillWindow(layout)
        val params = WindowManager.LayoutParams(
            layout.width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = WINDOW_TITLE
        }
        windowManager.addView(pillWindow.root, params)
        pillWindow.pill.expand()
        window = pillWindow
    }

    fun pushLevel(level: Float) {
        window?.pill?.pushLevel(level)
    }

    /** Shows the text recognised so far under the pill. */
    fun showPreview(text: String) {
        window?.showCaption(text)
    }

    fun showTranscribing() {
        window?.pill?.setPhase(RecordingPillView.Phase.Transcribing)
    }

    /**
     * Briefly shows a check mark, then hides the pill. The pill outlives the service that showed
     * it, so the confirmation stays visible after dictation has finished.
     */
    fun finishWithSuccess() {
        val pillWindow = window ?: return
        window = null
        finishingWindow = pillWindow
        pillWindow.pill.setPhase(RecordingPillView.Phase.Done)
        handler.postDelayed({ collapse(pillWindow) }, DONE_DISPLAY_MILLIS)
    }

    fun hide() {
        val pillWindow = window ?: return
        window = null
        collapse(pillWindow)
    }

    private fun collapse(pillWindow: PillWindow) {
        pillWindow.caption.visibility = View.GONE
        pillWindow.pill.collapse { remove(pillWindow) }
    }

    private fun remove(pillWindow: PillWindow) {
        if (finishingWindow == pillWindow) {
            finishingWindow = null
            handler.removeCallbacksAndMessages(null)
        }
        if (pillWindow.root.isAttachedToWindow) windowManager.removeViewImmediate(pillWindow.root)
    }

    private fun computeLayout(): PillLayout {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets
        val cutout = insets.displayCutout?.boundingRectTop
            ?.takeUnless { it.isEmpty }
            ?.let { CutoutBounds(it.left, it.top, it.right, it.bottom) }
        return PillLayout.compute(
            screenWidth = metrics.bounds.width(),
            statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top,
            cutout = cutout,
            density = density,
        )
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    /** The overlay window's content: the pill and, once there is text, a caption below it. */
    private inner class PillWindow(layout: PillLayout) {
        val pill = RecordingPillView(context, layout).apply { setOnClickListener { onTap() } }
        val caption = TextView(context).apply {
            setTextColor(CAPTION_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CAPTION_TEXT_SP)
            maxLines = CAPTION_LINES
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(CAPTION_BACKGROUND)
                cornerRadius = dp(16).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { onTap() }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pill)
            addView(
                caption,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }

        fun showCaption(text: String) {
            caption.text = newestWords(text)
            caption.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        /** About as much as fits in the caption, which is as narrow as the pill. */
        private const val CAPTION_MAX_CHARS = 72
        private const val WINDOW_TITLE = "Voicemator recording indicator"
        private const val DONE_DISPLAY_MILLIS = 900L
        private const val CAPTION_TEXT_SP = 14f
        private const val CAPTION_LINES = 3
        private const val CAPTION_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val CAPTION_BACKGROUND = 0xE6000000.toInt()

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * The end of [text], starting at a word, so the caption shows what was just said; a
         * multi-line TextView cannot ellipsize at the start by itself.
         */
        internal fun newestWords(text: String, maxChars: Int = CAPTION_MAX_CHARS): String {
            if (text.length <= maxChars) return text
            val tail = text.takeLast(maxChars)
            val firstWordStart = tail.indexOf(' ').takeIf { it >= 0 }?.plus(1) ?: 0
            return "…" + tail.substring(firstWordStart)
        }
    }
}
