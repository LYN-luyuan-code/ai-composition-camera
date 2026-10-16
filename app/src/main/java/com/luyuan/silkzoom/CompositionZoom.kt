package com.luyuan.silkzoom

/** Relative framing choice converted to an absolute camera zoom, applied in short steps. */
internal class CompositionZoom(val baseZoom: Float, requestedScale: Float, maxZoom: Float) {
    val goal = (baseZoom * requestedScale).coerceIn(baseZoom, maxZoom.coerceAtLeast(baseZoom))
    val totalScale: Float get() = goal / baseZoom
    fun remaining(actualZoom: Float): Float = (goal / actualZoom.coerceAtLeast(0.1f)).coerceAtLeast(1f)
    fun finished(actualZoom: Float): Boolean = actualZoom >= goal - 0.025f
    fun nextStep(actualZoom: Float): Float = minOf(goal, actualZoom * 1.22f)
}
