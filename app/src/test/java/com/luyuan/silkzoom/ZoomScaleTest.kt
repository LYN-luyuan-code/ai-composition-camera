package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class ZoomScaleTest {
    @Test fun rulerSpacesZoomDoublingsEvenlyAndRoundTrips() {
        for ((index, value) in listOf(1f, 2f, 4f, 8f).withIndex()) {
            assertEquals(index / 3f, ZoomScale.fraction(value, 1f, 8f), .00001f)
            assertEquals(value, ZoomScale.value(ZoomScale.fraction(value, 1f, 8f), 1f, 8f), .00001f)
        }
        assertEquals(1f, ZoomScale.value(-1f, 1f, 8f), .00001f)
        assertEquals(8f, ZoomScale.value(2f, 1f, 8f), .00001f)
    }
    @Test fun renderZoomCorrectionKeepsTheSubjectAlignedWithTheChangingCrop() {
        val plan = CompositionPlan(targetX = .7f, targetY = .3f, cropWidth = .65f, cropHeight = 1f)
        val before = plan.targetBeforeCrop(5f)
        for (zoom in listOf(1f, 1.5f, 3f, 5f)) {
            val mapped = CompositionPlan.mapZoomPoint(before.first, before.second, 1f, zoom)
            val target = plan.targetBeforeCrop(5f / zoom)
            assertEquals(target.first, mapped.first, .00001f)
            assertEquals(target.second, mapped.second, .00001f)
        }
    }
}
