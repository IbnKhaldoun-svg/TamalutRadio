package com.tamalut.radio

import com.tamalut.radio.core.preferences.OverlayEdge
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    @Test
    fun verticalPositionClampsInsideVisibleBounds() {
        assertEquals(0, OverlayGeometry.clampY(-40, screenHeight = 1000, windowHeight = 100))
        assertEquals(900, OverlayGeometry.clampY(1200, screenHeight = 1000, windowHeight = 100))
    }

    @Test
    fun normalizedPositionRoundTripsAcrossDifferentDisplayHeights() {
        val fraction = OverlayGeometry.normalizedVerticalFraction(
            y = 450,
            screenHeight = 1000,
            windowHeight = 100,
        )

        assertEquals(0.5f, fraction)
        assertEquals(
            950,
            OverlayGeometry.yFromNormalizedFraction(
                fraction = fraction,
                screenHeight = 2000,
                windowHeight = 100,
            ),
        )
    }

    @Test
    fun dragReleaseSnapsToNearestHorizontalEdge() {
        assertEquals(
            OverlayEdge.LEFT,
            OverlayGeometry.snapEdge(x = 80, screenWidth = 1000, windowWidth = 100),
        )
        assertEquals(
            OverlayEdge.RIGHT,
            OverlayGeometry.snapEdge(x = 780, screenWidth = 1000, windowWidth = 100),
        )
        assertEquals(0, OverlayGeometry.xForEdge(OverlayEdge.LEFT, 1000, 100))
        assertEquals(900, OverlayGeometry.xForEdge(OverlayEdge.RIGHT, 1000, 100))
    }

    @Test
    fun nonFiniteNormalizedPositionFallsBackSafely() {
        assertEquals(
            0,
            OverlayGeometry.yFromNormalizedFraction(
                fraction = Float.NaN,
                screenHeight = 1000,
                windowHeight = 100,
            ),
        )
    }
}
