package io.github.jakub_m_arch.voicemator.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Black pill drawn around the camera cutout while dictating, in the spirit of VoiceInk's notch.
 *
 * The left side shows a status dot and the elapsed time, the right side a live waveform. The
 * space between them is left empty for the camera.
 */
@SuppressLint("ViewConstructor")
class RecordingPillView(
    context: Context,
    private val layout: PillLayout,
) : View(context) {

    enum class Phase { Recording, Transcribing, Done }

    private val density = resources.displayMetrics.density
    private val levels = FloatArray(BAR_COUNT)
    private val startedAt = SystemClock.elapsedRealtime()
    private var phase = Phase.Recording
    private var expansion = 0f
    private var animator: ValueAnimator? = null

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PILL_COLOR }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3 * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = 13 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DONE_COLOR
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.5f * density
    }
    private val pillRect = RectF()
    private val checkPath = Path()

    fun expand() = animateExpansion(to = 1f, durationMillis = 420L) {}

    fun collapse(onCollapsed: () -> Unit) = animateExpansion(to = 0f, durationMillis = 220L, onCollapsed)

    fun pushLevel(level: Float) {
        levels.copyInto(levels, destinationOffset = 0, startIndex = 1)
        levels[BAR_COUNT - 1] = level
    }

    fun setPhase(phase: Phase) {
        this.phase = phase
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(layout.width, layout.height)
    }

    override fun onDraw(canvas: Canvas) {
        val pillHeight = (height - layout.pillTop).toFloat()
        val collapsedWidth = max(layout.cutoutWidth.toFloat(), pillHeight)
        val pillWidth = collapsedWidth + (width - collapsedWidth) * expansion
        val left = (width - pillWidth) / 2
        pillRect.set(left, layout.pillTop.toFloat(), left + pillWidth, height.toFloat())
        val radius = pillHeight / 2
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint)

        val contentAlpha = ((expansion - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (contentAlpha > 0f) drawContent(canvas, (contentAlpha * 255).toInt())
        if (phase != Phase.Done) postInvalidateOnAnimation()
    }

    private fun drawContent(canvas: Canvas, alpha: Int) {
        val centerY = layout.contentCenterY.toFloat()
        val padding = 14 * density
        val sideStart = pillRect.left + padding
        val sideEnd = pillRect.right - padding

        // Left: status dot and elapsed time.
        val dotRadius = 4.5f * density
        dotPaint.color = when (phase) {
            Phase.Recording -> RECORDING_COLOR
            Phase.Transcribing -> TRANSCRIBING_COLOR
            Phase.Done -> DONE_COLOR
        }
        val pulse = if (phase == Phase.Recording) 0.75f + 0.25f * wave(periodMillis = 1200L) else 1f
        dotPaint.alpha = (alpha * pulse).toInt()
        canvas.drawCircle(sideStart + dotRadius, centerY, dotRadius, dotPaint)

        textPaint.alpha = alpha
        val textBaseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(elapsedText(), sideStart + dotRadius * 2 + 7 * density, textBaseline, textPaint)

        // Right: waveform, an animated wave while transcribing, or a check mark when done.
        if (phase == Phase.Done) {
            drawCheck(canvas, sideEnd - 8 * density, centerY, alpha)
            return
        }
        val spacing = 5.5f * density
        val maxHeight = layout.contentHeight * 0.8f
        val minHeight = 3 * density
        barPaint.color = if (phase == Phase.Recording) TEXT_COLOR else TRANSCRIBING_COLOR
        barPaint.alpha = alpha
        for (i in 0 until BAR_COUNT) {
            val x = sideEnd - (BAR_COUNT - 1 - i) * spacing - barPaint.strokeWidth / 2
            val amount = when (phase) {
                Phase.Recording -> levels[i]
                else -> 0.25f + 0.25f * wave(periodMillis = 900L, offset = i / BAR_COUNT.toFloat())
            }
            val half = (minHeight + (maxHeight - minHeight) * amount) / 2
            canvas.drawLine(x, centerY - half, x, centerY + half, barPaint)
        }
    }

    private fun drawCheck(canvas: Canvas, centerX: Float, centerY: Float, alpha: Int) {
        val size = 7 * density
        checkPath.reset()
        checkPath.moveTo(centerX - size, centerY)
        checkPath.lineTo(centerX - size * 0.3f, centerY + size * 0.7f)
        checkPath.lineTo(centerX + size, centerY - size * 0.7f)
        checkPaint.alpha = alpha
        canvas.drawPath(checkPath, checkPaint)
    }

    private fun elapsedText(): String {
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** A smooth 0..1 oscillation over time, shifted by [offset] of a period. */
    private fun wave(periodMillis: Long, offset: Float = 0f): Float {
        val t = (SystemClock.uptimeMillis() % periodMillis) / periodMillis.toFloat() + offset
        return (sin(2 * PI * t).toFloat() + 1) / 2
    }

    private fun animateExpansion(to: Float, durationMillis: Long, onEnd: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(expansion, to).apply {
            duration = durationMillis
            if (to > expansion) interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                expansion = it.animatedValue as Float
                invalidate()
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    private companion object {
        const val BAR_COUNT = 9
        const val PILL_COLOR = 0xFF000000.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val RECORDING_COLOR = 0xFFFF453A.toInt()
        const val TRANSCRIBING_COLOR = 0xFFA78BFA.toInt()
        const val DONE_COLOR = 0xFF34D399.toInt()
    }
}
