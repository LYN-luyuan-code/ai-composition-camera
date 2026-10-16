package com.luyuan.silkzoom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Light touch smoothing and a separate, slower critically-damped button transition. */
internal class ZoomMotion {
    private var minZoom = 1f
    private var maxZoom = 1f
    private var position = 0f
    private var target = 0f
    private var velocity = 0f
    private var followingGesture = false
    private var framing = false
    private var framingStart = 0f
    private var framingElapsed = 0f
    private var framingDuration = 0f

    var initialized = false
        private set
    var isAnimating = false
        private set

    val zoom: Float get() = exp(position).coerceIn(minZoom, maxZoom)
    val targetZoom: Float get() = exp(target).coerceIn(minZoom, maxZoom)

    fun updateCapabilities(minZoom: Float, maxZoom: Float, observedZoom: Float) {
        this.minZoom = minZoom.coerceAtLeast(0.1f)
        this.maxZoom = maxZoom.coerceAtLeast(this.minZoom)
        if (!initialized) {
            position = ln(observedZoom.coerceIn(this.minZoom, this.maxZoom))
            target = position
            initialized = true
        } else {
            position = position.coerceIn(ln(this.minZoom), ln(this.maxZoom))
            target = target.coerceIn(ln(this.minZoom), ln(this.maxZoom))
        }
    }

    fun stopAtCurrent() {
        target = position
        velocity = 0f
        followingGesture = false
        framing = false
        isAnimating = false
    }

    fun trackTo(zoomRatio: Float) {
        if (!initialized) return
        position = ln(zoomRatio.coerceIn(minZoom, maxZoom))
        stopAtCurrent()
    }

    fun followTo(zoomRatio: Float) {
        if (!initialized) return
        target = ln(zoomRatio.coerceIn(minZoom, maxZoom))
        framing = false
        followingGesture = true
        velocity = 0f
        isAnimating = abs(target - position) > POSITION_EPSILON
        if (!isAnimating) trackTo(zoomRatio)
    }

    fun animateTo(zoomRatio: Float) {
        if (!initialized) return
        framing = false
        val clamped = zoomRatio.coerceIn(minZoom, maxZoom)
        target = ln(clamped)
        followingGesture = false
        if ((target - position) * velocity < 0f) velocity = 0f
        isAnimating = abs(target - position) > POSITION_EPSILON || abs(velocity) > VELOCITY_EPSILON
        if (!isAnimating) trackTo(clamped)
    }

    /** A single continuous framing move; slow enough for local tracking to follow each frame. */
    fun frameTo(zoomRatio: Float) {
        if (!initialized) return
        target = ln(zoomRatio.coerceIn(minZoom, maxZoom))
        framingStart = position
        framingElapsed = 0f
        framingDuration = (abs(target - position) * 1.05f).coerceAtLeast(.5f)
        followingGesture = false; velocity = 0f; framing = true
        isAnimating = abs(target - position) > POSITION_EPSILON
        if (!isAnimating) trackTo(zoomRatio)
    }

    fun advance(deltaSeconds: Float) {
        if (!isAnimating) return
        // Analytic critically-damped solution, stable at both 60/120Hz and after dropped frames.
        val dt = deltaSeconds.coerceIn(0f, 0.25f)
        if (framing) {
            framingElapsed = (framingElapsed + dt).coerceAtMost(framingDuration)
            val t = (framingElapsed.toDouble() / framingDuration).coerceIn(0.0, 1.0)
            val eased = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
            val next = (framingStart.toDouble() + (target - framingStart) * eased).toFloat()
            position = if (target >= framingStart) next.coerceIn(position, target) else next.coerceIn(target, position)
            if (t >= 1.0 || position == target) { position = target.coerceIn(ln(minZoom), ln(maxZoom)); stopAtCurrent() }
            return
        }
        val displacement = position - target
        if (followingGesture) {
            // 90% response in ~33ms: filter tiny touch steps without a long trailing spring.
            position = target + displacement * exp(-GESTURE_RESPONSE * dt)
        } else {
            val coefficient = velocity + BUTTON_RESPONSE * displacement
            val decay = exp(-BUTTON_RESPONSE * dt)
            position = target + (displacement + coefficient * dt) * decay
            velocity = (velocity - BUTTON_RESPONSE * coefficient * dt) * decay
        }
        val bounded = position.coerceIn(ln(minZoom), ln(maxZoom))
        if (bounded != position) velocity = 0f
        position = bounded
        if (abs(target - position) < POSITION_EPSILON && abs(velocity) < VELOCITY_EPSILON) {
            position = target
            stopAtCurrent()
        }
    }

    private companion object {
        const val BUTTON_RESPONSE = 14f
        const val GESTURE_RESPONSE = 70f
        const val POSITION_EPSILON = 0.0005f
        const val VELOCITY_EPSILON = 0.01f
    }
}
