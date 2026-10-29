package io.github.jakub_m_arch.voicemator.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillLayoutTest {

    @Test
    fun wrapsAroundCenteredCameraHole() {
        val hole = CutoutBounds(left = 507, top = 33, right = 573, bottom = 99)

        val layout = PillLayout.compute(SCREEN_WIDTH, STATUS_BAR, hole, DENSITY)

        assertEquals(0, layout.pillTop)
        assertEquals(hole.centerY, layout.contentCenterY)
        assertTrue("pill covers the hole", layout.height > hole.bottom)
        assertTrue("cutout gap fits the hole", layout.cutoutWidth > hole.width)
        assertFitsOnScreen(layout)
    }

    @Test
    fun floatsBelowStatusBarForWideNotch() {
        val notch = CutoutBounds(left = 260, top = 0, right = 820, bottom = 120)

        val layout = PillLayout.compute(SCREEN_WIDTH, STATUS_BAR, notch, DENSITY)

        assertTrue(layout.pillTop > STATUS_BAR)
        assertFitsOnScreen(layout)
    }

    @Test
    fun floatsBelowStatusBarForCornerCamera() {
        val corner = CutoutBounds(left = 40, top = 30, right = 100, bottom = 90)

        val layout = PillLayout.compute(SCREEN_WIDTH, STATUS_BAR, corner, DENSITY)

        assertTrue(layout.pillTop > STATUS_BAR)
    }

    @Test
    fun narrowScreenKeepsMargins() {
        val layout = PillLayout.compute(screenWidth = 320, statusBarHeight = 24, cutout = null, density = 1f)

        assertTrue(layout.width <= 320 - 32)
    }

    private fun assertFitsOnScreen(layout: PillLayout) {
        assertTrue("width ${layout.width} fits", layout.width <= SCREEN_WIDTH)
    }

    private companion object {
        const val SCREEN_WIDTH = 1080
        const val STATUS_BAR = 118
        const val DENSITY = 2.625f
    }
}
