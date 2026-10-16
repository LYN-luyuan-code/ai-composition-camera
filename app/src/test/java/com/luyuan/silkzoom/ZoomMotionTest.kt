package com.luyuan.silkzoom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMotionTest {
    private fun motion(start: Float = 1f) = ZoomMotion().apply {
        updateCapabilities(0.6f, 30f, start)
    }

    @Test
    fun gestureFollowsImmediatelyAndDoesNotDriftAfterRelease() {
        val motion = motion()
        motion.trackTo(2.4f)
        assertEquals(2.4f, motion.zoom, 0.0001f)
        assertFalse(motion.isAnimating)
        repeat(60) { motion.advance(1f / 60f) }
        assertEquals(2.4f, motion.zoom, 0.0001f)
    }

    @Test
    fun ultrawideButtonTransitionsSmoothlyInsteadOfHardJumping() {
        val motion = motion(3.2f)
        motion.animateTo(0.6f)
        assertTrue(motion.isAnimating)
        motion.advance(1f / 30f)
        assertTrue(motion.zoom > 0.6f && motion.zoom < 3.2f)
        var previous = motion.zoom
        repeat(36) {
            motion.advance(1f / 30f)
            assertTrue(motion.zoom <= previous)
            assertTrue(motion.zoom >= 0.6f)
            previous = motion.zoom
        }
        assertEquals(0.6f, motion.zoom, 0.0001f)
        motion.animateTo(1f)
        assertTrue(motion.isAnimating)
        repeat(36) { motion.advance(1f / 30f) }
        assertEquals(1f, motion.zoom, 0.0001f)
        assertFalse(motion.isAnimating)
    }

    @Test
    fun aboveOneButtonTransitionSettlesWithoutOvershoot() {
        val motion = motion()
        motion.animateTo(3.2f)
        assertTrue(motion.isAnimating)
        var previous = 1f
        repeat(48) {
            motion.advance(1f / 60f)
            assertTrue(motion.zoom >= previous)
            assertTrue(motion.zoom <= 3.2001f)
            previous = motion.zoom
        }
        assertFalse(motion.isAnimating)
        assertEquals(3.2f, motion.zoom, 0.0001f)
    }

    @Test
    fun springIsIndependentOfDisplayRefreshRate() {
        val sixtyHz = motion()
        val oneTwentyHz = motion()
        sixtyHz.animateTo(3.2f)
        oneTwentyHz.animateTo(3.2f)
        repeat(6) { sixtyHz.advance(1f / 60f) }
        repeat(12) { oneTwentyHz.advance(1f / 120f) }
        assertEquals(sixtyHz.zoom, oneTwentyHz.zoom, 0.0001f)
    }

    @Test
    fun newGestureInterruptsButtonAnimationWithoutAReleaseSpring() {
        val motion = motion()
        motion.animateTo(3.2f)
        motion.advance(1f / 60f)
        motion.stopAtCurrent()
        motion.trackTo(1.2f)
        repeat(24) { motion.advance(1f / 60f) }
        assertEquals(1.2f, motion.zoom, 0.0001f)
        assertFalse(motion.isAnimating)
    }

    @Test
    fun delayedZoomStateDoesNotReplaceTheNewTouchTarget() {
        val motion = motion()
        motion.trackTo(2.4f)
        motion.updateCapabilities(0.6f, 30f, 1.2f)
        assertEquals(2.4f, motion.zoom, 0.0001f)
    }

    @Test
    fun cameraBoundsApplyToBothGesturesAndButtons() {
        val motion = motion()
        motion.trackTo(100f)
        assertEquals(30f, motion.zoom, 0.0001f)
        motion.animateTo(0.1f)
        repeat(60) { motion.advance(1f / 60f) }
        assertEquals(0.6f, motion.zoom, 0.0001f)
        motion.updateCapabilities(1f, 10f, 1f)
        assertEquals(1f, motion.zoom, 0.0001f)
        motion.animateTo(100f)
        repeat(48) { motion.advance(1f / 60f) }
        assertEquals(10f, motion.zoom, 0.0001f)
    }

    @Test
    fun softenedGestureRemainsResponsiveAndSettlesWithoutDrift() {
        val motion = motion()
        motion.followTo(2.4f)
        motion.advance(0.04f)
        assertTrue(kotlin.math.ln(motion.zoom) / kotlin.math.ln(2.4f) > 0.9f)
        assertTrue(motion.zoom < 2.4f)
        motion.advance(0.10f)
        assertEquals(2.4f, motion.zoom, 0.0001f)
        assertFalse(motion.isAnimating)
    }

    @Test
    fun buttonReversalDoesNotContinueInTheOldDirection() {
        val motion = motion()
        motion.animateTo(3.2f)
        motion.advance(0.1f)
        val beforeReverse = motion.zoom
        motion.animateTo(0.6f)
        motion.advance(1f / 60f)
        assertTrue(motion.zoom < beforeReverse)
        repeat(60) { motion.advance(1f / 60f) }
        assertEquals(0.6f, motion.zoom, 0.0001f)
    }
    @Test fun framingIsContinuousMonotonicAndHasNoStepPauses() {
        val motion = motion()
        motion.frameTo(8f)
        motion.advance(1f / 60f)
        assertTrue(motion.zoom > 1f && motion.zoom < 1.01f)
        var previous = motion.zoom
        repeat(150) {
            motion.advance(1f / 60f)
            assertTrue(motion.zoom >= previous && motion.zoom <= 8.0001f)
            if (motion.isAnimating && motion.zoom < 7.999f) assertTrue(motion.zoom > previous)
            previous = motion.zoom
        }
        assertEquals(8f, motion.zoom, .0001f)
        assertFalse(motion.isAnimating)
    }

    @Test fun framingIsIndependentOfRefreshRateAndCanBeInterrupted() {
        val sixty = motion(); val oneTwenty = motion()
        sixty.frameTo(5f); oneTwenty.frameTo(5f)
        repeat(30) { sixty.advance(1f / 60f) }
        repeat(60) { oneTwenty.advance(1f / 120f) }
        assertEquals(sixty.zoom, oneTwenty.zoom, .0001f)
        val stopped = sixty.zoom
        sixty.stopAtCurrent()
        repeat(120) { sixty.advance(1f / 60f) }
        assertEquals(stopped, sixty.zoom, .0001f)
        sixty.followTo(1.5f)
        repeat(30) { sixty.advance(1f / 60f) }
        assertEquals(1.5f, sixty.zoom, .0001f)
    }

    @Test fun framingLimitsScaleChangeBetweenTrackingSamples() {
        val motion = motion()
        motion.frameTo(8f)
        var previous = motion.zoom
        repeat(32) {
            motion.advance(.075f)
            assertTrue(motion.zoom / previous < 1.25f)
            previous = motion.zoom
        }
        assertEquals(8f, motion.zoom, .0001f)
    }

}
