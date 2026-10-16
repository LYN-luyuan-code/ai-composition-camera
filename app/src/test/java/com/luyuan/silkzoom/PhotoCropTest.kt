package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class PhotoCropTest {
    @Test fun softwareZoomUsesSameCenteredFractionAsPreview() {
        val crop = PhotoCrop.centered(400, 100, 3200, 2400, 2f)
        assertEquals(PhotoCrop(1200, 700, 1600, 1200), crop)
        assertEquals(2000, crop.left + crop.width / 2)
        assertEquals(1300, crop.top + crop.height / 2)
    }
    @Test fun arbitraryCropFractionsAreAppliedToPixelsRatherThanStretched() {
        assertEquals(PhotoCrop(1200, 1090, 1600, 420), PhotoCrop.centered(400, 100, 3200, 2400, 2f, 1f, .35f))
        assertEquals(PhotoCrop(1680, 700, 640, 1200), PhotoCrop.centered(400, 100, 3200, 2400, 2f, .4f, 1f))
    }

    @Test fun portraitCaptureSwapsRawAxesSoSavedAspectMatchesDisplayedCrop() {
        for (rotation in listOf(90, 270)) {
            val crop = PhotoCrop.forDisplay(400, 100, 3200, 2400, 2f, rotation, 1f, .35f)
            assertEquals(PhotoCrop(1720, 700, 560, 1200), crop)
            assertEquals(1200f / 560f, crop.height.toFloat() / crop.width, .00001f)
        }
        for (rotation in listOf(0, 180)) {
            assertEquals(PhotoCrop(1200, 1090, 1600, 420), PhotoCrop.forDisplay(400, 100, 3200, 2400, 2f, rotation, 1f, .35f))
        }
    }

    @Test fun freeCropBoundsStayInsideViewportAtEveryRotationAndZoom() {
        for (rotation in listOf(0, 90, 180, 270)) for (zoom in listOf(1f, 2f, 8f)) {
            val crop = PhotoCrop.forDisplay(7, 11, 959, 1279, zoom, rotation, .23f, .67f)
            assertTrue(crop.width > 0 && crop.height > 0)
            assertTrue(crop.left >= 7 && crop.top >= 11)
            assertTrue(crop.left + crop.width <= 966 && crop.top + crop.height <= 1290)
        }
    }

    @Test fun neverLeavesViewportEvenAtHighZoom() {
        for (zoom in listOf(1f, 1.5f, 3f, 8f)) {
            val crop = PhotoCrop.centered(7, 11, 959, 1279, zoom)
            assertTrue(crop.left >= 7 && crop.top >= 11)
            assertTrue(crop.left + crop.width <= 966 && crop.top + crop.height <= 1290)
            assertEquals(959f / 1279, crop.width.toFloat() / crop.height, .01f)
        }
    }
}
