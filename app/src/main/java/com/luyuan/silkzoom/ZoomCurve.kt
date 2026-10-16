package com.luyuan.silkzoom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign

/** Pure math used by touch gestures. Keeping this separate makes the feel easy to tune. */
internal object ZoomCurve {
    private const val DRAG_EXPONENT = 1.45f
    private const val FULL_DRAG_MULTIPLIER = 6.5f
    private const val PINCH_RESPONSE = 0.82f

    /**
     * Maps drag distance to zoom in logarithmic space. The exponent makes small movements
     * precise, while a committed movement accelerates quickly through the range.
     */
    fun fromDrag(
        anchorZoom: Float,
        distancePx: Float,
        referenceTravelPx: Float,
        minZoom: Float,
        maxZoom: Float,
    ): Float {
        val normalized = distancePx / referenceTravelPx.coerceAtLeast(1f)
        val curved = normalized.sign * abs(normalized).pow(DRAG_EXPONENT)
        val logDelta = curved * ln(FULL_DRAG_MULTIPLIER)
        return (anchorZoom * exp(logDelta)).coerceIn(minZoom, maxZoom)
    }

    /** Pinch is multiplicative, so the same finger motion feels consistent at every lens. */
    fun fromPinch(
        currentTarget: Float,
        scaleFactor: Float,
        minZoom: Float,
        maxZoom: Float,
    ): Float {
        val softenedScale = scaleFactor.coerceIn(0.7f, 1.3f).pow(PINCH_RESPONSE)
        return (currentTarget * softenedScale).coerceIn(minZoom, maxZoom)
    }
}
