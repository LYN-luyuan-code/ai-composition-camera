package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Continuous, subpixel camera motions rather than just an exact copied template. */
class CompositionFlowTest {
    private val width = 160
    private val height = 240
    private fun scene(): IntArray = IntArray(width * height) { index ->
        val x = (index % width).toDouble(); val y = (index / width).toDouble()
        val value = (125 + 30 * sin(x * 0.39 + y * 0.12) + 27 * cos(y * 0.31 - x * 0.16) +
            16 * sin(x * 0.69) * cos(y * 0.53) + 12 * cos(x * 0.08 + y * 0.1)).roundToInt().coerceIn(0, 255)
        value * 0x010101
    }
    private fun transformed(source: IntArray, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f, brightness: Int = 0): IntArray =
        IntArray(source.size) { index ->
            val x = ((index % width - width / 2f - dx) / scale + width / 2f).coerceIn(0f, width - 1.01f)
            val y = ((index / width - height / 2f - dy) / scale + height / 2f).coerceIn(0f, height - 1.01f)
            val ix = x.toInt(); val iy = y.toInt(); val fx = x - ix; val fy = y - iy
            val top = (source[iy * width + ix] and 255) * (1 - fx) + (source[iy * width + ix + 1] and 255) * fx
            val bottom = (source[(iy + 1) * width + ix] and 255) * (1 - fx) + (source[(iy + 1) * width + ix + 1] and 255) * fx
            (top * (1 - fy) + bottom * fy + brightness).roundToInt().coerceIn(0, 255) * 0x010101
        }

    @Test fun followsSubpixelMotionAndExposureChangesAcrossManyFrames() {
        val source = scene()
        val tracker = CompositionTracker(width, height, source)
        for (step in 1..18) {
            val dx = step * 1.7f; val dy = step * -1.1f
            val match = tracker.locate(transformed(source, dx, dy, brightness = step))
            assertNotNull("Lost at frame $step", match)
            assertEquals((width / 2f + dx) / width, match!!.x, 0.02f)
            assertEquals((height / 2f + dy) / height, match.y, 0.02f)
        }
    }

    @Test fun retainsSubjectThroughGradualCropZoom() {
        val source = scene()
        val tracker = CompositionTracker(width, height, source, 0.4f, 0.4f)
        for (step in 1..10) {
            val scale = 1f + step * 0.012f
            val match = tracker.locate(transformed(source, scale = scale), 1.12f)
            assertNotNull("Lost at zoom $scale", match)
            assertEquals(0.5f + (0.4f - 0.5f) * scale, match!!.x, 0.02f)
            assertEquals(0.5f + (0.4f - 0.5f) * scale, match.y, 0.02f)
        }
    }

    @Test fun retainsSelectedSubjectThroughLargeProgressiveZoom() {
        val source = scene()
        val tracker = CompositionTracker(width, height, source, 0.45f, 0.45f)
        for (step in 1..60) {
            val scale = exp(ln(3f) * step / 60f)
            val match = tracker.locate(transformed(source, scale = scale), scale)
            assertNotNull("Lost at large zoom $scale", match)
            assertEquals(0.5f + (0.45f - 0.5f) * scale, match!!.x, 0.025f)
            assertEquals(0.5f + (0.45f - 0.5f) * scale, match.y, 0.025f)
        }
    }

    @Test fun doesNotLearnAnOccluderAndRecoversWhenSubjectReturns() {
        val source = scene()
        val tracker = CompositionTracker(width, height, source)
        assertNotNull(tracker.locate(source))
        assertNull(tracker.locate(IntArray(source.size) { 0x777777 }))
        val match = tracker.locate(transformed(source, 3f, 2f))
        assertNotNull(match)
        assertEquals(0.5f + 3f / width, match!!.x, 0.02f)
        assertEquals(0.5f + 2f / height, match.y, 0.02f)
    }

    @Test fun toleratesPartialOcclusionUsingOtherFeaturePoints() {
        val source = scene()
        val tracker = CompositionTracker(width, height, source)
        val moved = transformed(source, 2f, 1f)
        for (y in 110 until 117) for (x in 67 until 79) moved[y * width + x] = 0x777777
        val match = tracker.locate(moved)
        assertNotNull(match)
        assertEquals(0.5f + 2f / width, match!!.x, 0.025f)
        assertEquals(0.5f + 1f / height, match.y, 0.025f)
    }
}
