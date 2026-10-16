package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class StreetFilterTest {
    private fun color(matrix: FloatArray, red: Float, green: Float, blue: Float): FloatArray =
        FloatArray(3) { row -> matrix[row * 5] * red + matrix[row * 5 + 1] * green + matrix[row * 5 + 2] * blue + matrix[row * 5 + 4] }
    @Test fun zeroStrengthPreservesOriginalForEveryPreset() {
        val identity = StreetFilter.ORIGINAL.matrix(1f)
        StreetFilter.entries.forEach { assertArrayEquals(identity, it.matrix(0f), .0001f) }
    }
    @Test fun monochromeProducesEqualColorChannels() {
        val result = color(StreetFilter.MONO.matrix(1f), 180f, 90f, 30f)
        assertEquals(result[0], result[1], .0001f)
        assertEquals(result[1], result[2], .0001f)
    }
    @Test fun strengthInterpolatesAndNeverChangesAlpha() {
        StreetFilter.entries.forEach {
            val full = color(it.matrix(1f), 100f, 80f, 60f)
            val half = color(it.matrix(.5f), 100f, 80f, 60f)
            assertEquals((100f + full[0]) / 2, half[0], .0001f)
            assertArrayEquals(floatArrayOf(0f,0f,0f,1f,0f), it.matrix(1f).copyOfRange(15,20), .0001f)
        }
    }
}
