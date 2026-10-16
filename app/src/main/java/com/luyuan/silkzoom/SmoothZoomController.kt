package com.luyuan.silkzoom

import android.animation.ValueAnimator
import android.view.Choreographer

/** Animates software cropping. Camera hardware stays at its primary 1x lens. */
internal class SmoothZoomController(private val onZoomFrame: (Float) -> Unit) {
    private val choreographer = Choreographer.getInstance()
    private val motion = ZoomMotion()
    val isAnimating: Boolean get() = motion.isAnimating
    private var scheduled = false
    private var cancelled = false
    private var lastFrame = 0L
    private var lastPublished = Float.NaN
    private val frameCallback = Choreographer.FrameCallback { time ->
        scheduled = false
        if (!cancelled) {
            val delta = if (lastFrame == 0L) 1f / 60f else ((time - lastFrame) / 1_000_000_000f).coerceIn(0f, .25f)
            lastFrame = time
            motion.advance(delta)
            publish()
            if (motion.isAnimating) schedule() else lastFrame = 0L
        }
    }
    fun updateCapabilities(minZoom: Float, maxZoom: Float, observedZoom: Float) { motion.updateCapabilities(minZoom, maxZoom, observedZoom); publish() }
    fun beginInteraction() { stop(); motion.stopAtCurrent() }
    fun endInteraction() { if (!cancelled && motion.initialized) schedule() }
    fun trackTo(zoom: Float) { if (!cancelled && motion.initialized) { motion.trackTo(zoom); publish(); schedule() } }
    fun followTo(zoom: Float) { if (!cancelled && motion.initialized) { motion.followTo(zoom); schedule() } }
    fun animateTo(zoom: Float) { if (!cancelled && motion.initialized) { motion.animateTo(zoom); schedule() } }
    fun frameTo(zoom: Float) {
        if (cancelled || !motion.initialized) return
        stop()
        if (ValueAnimator.areAnimatorsEnabled()) { motion.frameTo(zoom); schedule() }
        else { motion.trackTo(zoom); publish() }
    }
    fun cancel() { cancelled = true; stop() }
    private fun schedule() { if (!scheduled && !cancelled) { scheduled = true; choreographer.postFrameCallback(frameCallback) } }
    private fun stop() { choreographer.removeFrameCallback(frameCallback); scheduled = false; lastFrame = 0L }
    private fun publish() { if (lastPublished != motion.zoom) { lastPublished = motion.zoom; onZoomFrame(motion.zoom) } }
}
