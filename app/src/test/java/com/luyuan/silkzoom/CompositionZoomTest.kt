package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class CompositionZoomTest {
    @Test fun reachesLargeZoomInBoundedStepsRatherThanOneJump() {
        val zoom = CompositionZoom(1f, 5f, 10f)
        var actual = 1f
        var steps = 0
        while (!zoom.finished(actual)) {
            val next = zoom.nextStep(actual)
            assertTrue(next > actual)
            assertTrue(next / actual <= 1.2201f)
            actual = next
            assertTrue(++steps < 20)
        }
        assertTrue(steps > 1)
        assertEquals(5f, actual, 0.001f)
        assertEquals(1f, zoom.remaining(actual), 0.001f)
    }

    @Test fun startsFromCurrentLensAndRespectsMaximumZoom() {
        val zoom = CompositionZoom(3.2f, 5f, 10f)
        assertEquals(10f, zoom.goal, 0.001f)
        assertEquals(10f / 3.2f, zoom.totalScale, 0.001f)
        assertEquals(2f, zoom.remaining(5f), 0.001f)
    }

    @Test fun alignmentCompensationRemainsCorrectAfterEveryStep() {
        val zoom = CompositionZoom(1f, 3f, 10f)
        val plan = CompositionPlan(targetX = 0.33f, targetY = 0.4f, cropScale = 3f)
        var actual = 1f
        while (!zoom.finished(actual)) {
            val before = plan.targetBeforeCrop(zoom.remaining(actual))
            val next = zoom.nextStep(actual)
            val after = plan.targetBeforeCrop(zoom.remaining(next))
            assertEquals(after.first, 0.5f + (before.first - 0.5f) * next / actual, 0.0001f)
            assertEquals(after.second, 0.5f + (before.second - 0.5f) * next / actual, 0.0001f)
            actual = next
        }
    }
}
