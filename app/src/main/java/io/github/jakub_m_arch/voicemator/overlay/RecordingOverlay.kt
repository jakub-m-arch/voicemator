package io.github.jakub_m_arch.voicemator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService

/**
 * Shows [RecordingPillView] at the top of the screen, wrapped around the camera cutout when the
 * device has one. Requires the "Display over other apps" permission; without it nothing is shown.
 */
class RecordingOverlay(context: Context, private val onTap: () -> Unit) {

    private val context = context.applicationContext
    private val windowManager = checkNotNull(context.getSystemService<WindowManager>())
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private var pill: RecordingPillView? = null

    /** A pill still showing its result after dictation ended. */
    private var finishingPill: RecordingPillView? = null

    fun show() {
        if (pill != null || !canShow(context)) return
        finishingPill?.let(::remove)
        val layout = computeLayout()
        val view = RecordingPillView(context, layout).apply { setOnClickListener { onTap() } }
        val params = WindowManager.LayoutParams(
            layout.width,
            layout.height,
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
        windowManager.addView(view, params)
        view.expand()
        pill = view
    }

    fun pushLevel(level: Float) {
        pill?.pushLevel(level)
    }

    fun showTranscribing() {
        pill?.setPhase(RecordingPillView.Phase.Transcribing)
    }

    /**
     * Briefly shows a check mark, then hides the pill. The pill outlives the service that showed
     * it, so the confirmation stays visible after dictation has finished.
     */
    fun finishWithSuccess() {
        val view = pill ?: return
        pill = null
        finishingPill = view
        view.setPhase(RecordingPillView.Phase.Done)
        handler.postDelayed({ collapse(view) }, DONE_DISPLAY_MILLIS)
    }

    fun hide() {
        val view = pill ?: return
        pill = null
        collapse(view)
    }

    private fun collapse(view: RecordingPillView) {
        view.collapse { remove(view) }
    }

    private fun remove(view: RecordingPillView) {
        if (finishingPill == view) {
            finishingPill = null
            handler.removeCallbacksAndMessages(null)
        }
        if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
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

    companion object {
        private const val WINDOW_TITLE = "Voicemator recording indicator"
        private const val DONE_DISPLAY_MILLIS = 900L

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
