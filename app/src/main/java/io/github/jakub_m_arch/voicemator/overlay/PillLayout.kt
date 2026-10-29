package io.github.jakub_m_arch.voicemator.overlay

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounds of the display cutout at the top edge, in pixels. */
data class CutoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

/** Size of the pill window, where the pill starts and where its content row sits, in pixels. */
data class PillLayout(
    val width: Int,
    val height: Int,
    val pillTop: Int,
    val cutoutWidth: Int,
    val contentCenterY: Int,
    val contentHeight: Int,
) {
    companion object {
        private const val SIDE_WIDTH_DP = 84
        private const val PILL_HEIGHT_DP = 34
        private const val CUTOUT_GAP_DP = 6
        private const val MAX_HOLE_SIZE_DP = 48
        private const val SCREEN_MARGIN_DP = 16

        /**
         * Wraps the pill around a small, centered camera hole, which makes the camera look like
         * part of it. Any other cutout, such as a wide notch or a corner camera, or none at all,
         * puts the pill just below the status bar instead.
         */
        fun compute(screenWidth: Int, statusBarHeight: Int, cutout: CutoutBounds?, density: Float): PillLayout {
            fun dp(value: Int) = (value * density).roundToInt()
            val pillHeight = dp(PILL_HEIGHT_DP)
            val maxWidth = screenWidth - dp(SCREEN_MARGIN_DP) * 2

            if (cutout != null && isCenteredHole(cutout, screenWidth, dp(MAX_HOLE_SIZE_DP))) {
                val gap = dp(CUTOUT_GAP_DP)
                val cutoutWidth = cutout.width + gap * 2
                return PillLayout(
                    width = min(cutoutWidth + dp(SIDE_WIDTH_DP) * 2, maxWidth),
                    height = max(cutout.bottom + gap, pillHeight),
                    pillTop = 0,
                    cutoutWidth = cutoutWidth,
                    contentCenterY = cutout.centerY,
                    contentHeight = max(cutout.height, pillHeight / 2),
                )
            }

            val pillTop = statusBarHeight + dp(CUTOUT_GAP_DP)
            return PillLayout(
                width = min(pillHeight + dp(SIDE_WIDTH_DP) * 2, maxWidth),
                height = pillTop + pillHeight,
                pillTop = pillTop,
                cutoutWidth = pillHeight,
                contentCenterY = pillTop + pillHeight / 2,
                contentHeight = pillHeight / 2,
            )
        }

        private fun isCenteredHole(cutout: CutoutBounds, screenWidth: Int, maxHoleSize: Int): Boolean =
            cutout.width <= maxHoleSize &&
                cutout.height <= maxHoleSize &&
                abs(cutout.centerX - screenWidth / 2) <= cutout.width / 2
    }
}
