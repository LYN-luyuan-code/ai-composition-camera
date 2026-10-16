package com.luyuan.silkzoom

/** Camera-paced latest-only requests, independent of display FPS and capture-result latency. */
internal class LatestZoomRequests(
    frameRate: Int = 30,
    private val submit: (Float, (Throwable?) -> Unit) -> Unit,
) {
    // Allow display-vsync jitter so a 30fps camera doesn't accidentally get 20fps zoom.
    private val intervalNanos = 1_000_000_000L / frameRate.coerceIn(15, 60) - 1_000_000L
    private var lastSubmissionNanos: Long? = null
    private var submittedZoom: Float? = null
    private var pendingZoom: Float? = null
    private var activeRequest: Long? = null
    private var nextRequest = 0L
    private var cancelled = false

    val hasPending: Boolean get() = pendingZoom != null && !cancelled

    fun offer(zoom: Float) {
        if (cancelled) return
        pendingZoom = zoom.takeIf { it != submittedZoom }
    }

    fun flush(frameTimeNanos: Long) {
        if (cancelled) return
        val zoom = pendingZoom ?: return
        val previous = lastSubmissionNanos
        if (previous != null && frameTimeNanos - previous < intervalNanos) return
        pendingZoom = null
        lastSubmissionNanos = frameTimeNanos
        submittedZoom = zoom
        val token = ++nextRequest
        activeRequest = token
        try {
            submit(zoom) { error ->
                if (!cancelled && activeRequest == token) {
                    activeRequest = null
                    if (error != null) submittedZoom = null
                }
            }
        } catch (_: RuntimeException) {
            if (activeRequest == token) {
                activeRequest = null
                submittedZoom = null
            }
        }
    }

    fun cancel() {
        cancelled = true
        pendingZoom = null
        activeRequest = null
    }
}
