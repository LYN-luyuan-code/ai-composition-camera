package com.luyuan.silkzoom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomCurveTest {
    @Test
    fun noMovementKeepsAnchor() {
        assertEquals(1f, ZoomCurve.fromDrag(1f, 0f, 300f, 0.6f, 30f), 0.0001f)
    }

    @Test
    fun smallMovementIsPreciseAndLargeMovementAccelerates() {
        val small = ZoomCurve.fromDrag(1f, 50f, 300f, 0.6f, 30f)
        val medium = ZoomCurve.fromDrag(1f, 150f, 300f, 0.6f, 30f)
        val large = ZoomCurve.fromDrag(1f, 300f, 300f, 0.6f, 30f)

        assertTrue(small in 1f..1.3f)
        assertTrue(medium > small)
        assertTrue((large - medium) > (medium - small))
    }

    @Test
    fun resultsAreClampedToCameraRange() {
        assertEquals(10f, ZoomCurve.fromDrag(1f, 2_000f, 300f, 0.6f, 10f), 0.0001f)
        assertEquals(0.6f, ZoomCurve.fromDrag(1f, -2_000f, 300f, 0.6f, 10f), 0.0001f)
    }
}
