package com.luyuan.silkzoom

import kotlin.math.exp
import kotlin.math.ln

/** A logarithmic ruler gives every doubling of zoom the same amount of travel. */
internal object ZoomScale {
    fun fraction(zoom: Float, min: Float, max: Float): Float =
        ((ln(zoom.coerceIn(min, max)) - ln(min)) / (ln(max) - ln(min)).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
    fun value(fraction: Float, min: Float, max: Float): Float =
        exp(ln(min) + fraction.coerceIn(0f, 1f) * (ln(max) - ln(min))).coerceIn(min, max)
}
