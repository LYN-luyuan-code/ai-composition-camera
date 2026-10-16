package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CompositionTrackerTest {
    private val width = 96
    private val height = 128
    private fun textured(): IntArray {
        val random = Random(123)
        return IntArray(width * height) {
            val value = random.nextInt(256)
            (value shl 16) or (value shl 8) or value
        }
    }

    @Test fun followsReferenceAfterCameraMovement() {
        val original = textured()
        val tracker = CompositionTracker(width, height, original)
        val moved = IntArray(original.size)
        for (y in 0 until height - 20) for (x in 0 until width - 14) {
            moved[y * width + x] = original[(y + 20) * width + x + 14]
        }
        val match = tracker.locate(moved)
        assertNotNull(match)
        assertEquals(34f / width, match!!.x, 0.001f)
        assertEquals(44f / height, match.y, 0.001f)
    }

    @Test fun tracksAiSelectedSubjectAwayFromImageCenter() {
        val original = textured()
        val tracker = CompositionTracker(width, height, original, 0.25f, 0.375f)
        val match = tracker.locate(original)
        assertNotNull(match)
        assertEquals(0.25f, match!!.x, 0.001f)
        assertEquals(0.375f, match.y, 0.001f)
    }

    @Test fun arbitraryAiCoordinatesAreSnappedToTheTrackingSearchGrid() {
        val original = textured()
        val tracker = CompositionTracker(width, height, original, 0.517f, 0.397f)
        val match = tracker.locate(original)
        assertNotNull(match)
        assertEquals(0.517f, match!!.x, 0.02f)
        assertEquals(0.397f, match.y, 0.02f)
    }

    @Test fun rejectsFeaturelessSubjectsAndMissingReference() {
        val blank = IntArray(width * height) { 0x808080 }
        assertNull(CompositionTracker(width, height, blank).locate(blank))
        assertNull(CompositionTracker(width, height, textured()).locate(blank))
    }

    @Test fun keepsUserSelectionWhenASimilarPatchAppearsElsewhere() {
        val original = textured()
        val ambiguous = original.copyOf()
        for (y in -6 until 6) for (x in -6 until 6) {
            ambiguous[(height / 2 + y) * width + 20 + x] = original[(height / 2 + y) * width + width / 2 + x]
        }
        val match = CompositionTracker(width, height, original).locate(ambiguous)
        assertNotNull(match)
        assertEquals(0.5f, match!!.x, 0.02f)
        assertEquals(0.5f, match.y, 0.02f)
    }
    @Test fun sceneMappingPreservesAnOffCenterSubjectAcrossCameraMovement() {
        val original = textured()
        val tracker = CompositionTracker(width, height, original, wholeFrame = true)
        for (step in 1..5) {
            val moved = IntArray(original.size)
            for (y in 0 until height - step) for (x in 0 until width - step * 2)
                moved[y * width + x] = original[(y + step) * width + x + step * 2]
            assertNotNull(tracker.locate(moved))
            val mapped = tracker.mapOriginalPoint(.3f, .7f)
            assertEquals(.3f - step * 2f / width, mapped.first, .015f)
            assertEquals(.7f - step.toFloat() / height, mapped.second, .015f)
        }
    }

    @Test fun emptyCenterDoesNotPreventTrackingTheSceneWhileAiSearches() {
        val original = textured()
        for (y in height / 3..height * 2 / 3) for (x in width / 3..width * 2 / 3)
            original[y * width + x] = 0x808080
        val tracker = CompositionTracker(width, height, original, wholeFrame = true)
        assertNotNull(tracker.locate(original))
        val point = tracker.mapOriginalPoint(.2f, .8f)
        assertEquals(.2f, point.first, .001f)
        assertEquals(.8f, point.second, .001f)
    }

}
