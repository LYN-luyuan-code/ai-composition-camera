package com.luyuan.silkzoom

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestZoomRequestsTest {
    private class FakeCamera {
        val values = mutableListOf<Float>()
        val completions = mutableListOf<(Throwable?) -> Unit>()
        val requests = LatestZoomRequests { zoom, complete ->
            values += zoom
            completions += complete
        }

        fun frame(zoom: Float, millis: Long) {
            requests.offer(zoom)
            requests.flush(millis * 1_000_000L)
        }
    }

    @Test
    fun latestFrameNeverWaitsForOlderCaptureAcknowledgement() {
        val camera = FakeCamera()
        camera.frame(1.2f, 0)
        camera.frame(1.6f, 33)
        camera.frame(2f, 66)
        assertEquals(listOf(1.2f, 1.6f, 2f), camera.values)
    }

    @Test
    fun restingValueIsNotSubmittedAgain() {
        val camera = FakeCamera()
        repeat(60) { camera.frame(1.2f, it * 33L) }
        camera.completions.first()(null)
        repeat(60) { camera.frame(1.2f, (it + 60) * 33L) }
        assertEquals(listOf(1.2f), camera.values)
    }

    @Test
    fun ultrawideAndRapidReverseSupersedeOlderRequests() {
        val camera = FakeCamera()
        camera.frame(3.2f, 0)
        camera.frame(0.6f, 33)
        camera.frame(1f, 66)
        assertEquals(listOf(3.2f, 0.6f, 1f), camera.values)
    }

    @Test
    fun cancelledOlderCompletionDoesNotInvalidateLatestTarget() {
        val camera = FakeCamera()
        camera.frame(2f, 0)
        camera.frame(0.6f, 33)
        camera.completions[0](IllegalStateException("superseded"))
        camera.frame(0.6f, 66)
        camera.completions[1](null)
        camera.frame(0.6f, 99)
        assertEquals(listOf(2f, 0.6f), camera.values)
    }

    @Test
    fun failedLatestRequestCanBeRetriedByNextInput() {
        val camera = FakeCamera()
        camera.frame(2f, 0)
        camera.completions[0](IllegalStateException("camera closed"))
        camera.frame(2f, 33)
        assertEquals(listOf(2f, 2f), camera.values)
    }

    @Test
    fun synchronousFailureDoesNotBlockFollowingInput() {
        val values = mutableListOf<Float>()
        val requests = LatestZoomRequests { zoom, complete ->
            values += zoom
            if (values.size == 1) throw IllegalStateException("closed")
            complete(null)
        }
        requests.offer(1.2f)
        requests.flush(0L)
        requests.offer(1.2f)
        requests.flush(33_000_000L)
        requests.offer(2f)
        requests.flush(66_000_000L)
        assertEquals(listOf(1.2f, 1.2f, 2f), values)
    }

    @Test
    fun cancelStopsRequestsAndIgnoresOldCompletions() {
        val camera = FakeCamera()
        camera.frame(2f, 0)
        camera.requests.cancel()
        camera.completions[0](IllegalStateException("closed"))
        camera.frame(0.6f, 33)
        assertEquals(listOf(2f), camera.values)
    }

    @Test
    fun latestPendingValueReplacesIntermediateDisplayFrames() {
        val camera = FakeCamera()
        camera.frame(1f, 0)
        camera.frame(1.2f, 8)
        camera.frame(1.4f, 16)
        camera.frame(1.6f, 24)
        assertEquals(listOf(1f), camera.values)
        camera.requests.flush(33_000_000L)
        assertEquals(listOf(1f, 1.6f), camera.values)
    }

    @Test
    fun reversalBackToSubmittedValueClearsStalePendingValue() {
        val camera = FakeCamera()
        camera.frame(1f, 0)
        camera.frame(2f, 16)
        camera.frame(1f, 24)
        camera.requests.flush(33_000_000L)
        assertEquals(listOf(1f), camera.values)
    }

    @Test
    fun oneTwentyHzDisplayDoesNotFloodThirtyFpsCamera() {
        val values = mutableListOf<Float>()
        val requests = LatestZoomRequests(30) { zoom, _ -> values += zoom }
        repeat(121) {
            requests.offer(1f + it / 120f)
            requests.flush(it * (1_000_000_000L / 120))
        }
        assertEquals(31, values.size)
        assertEquals(2f, values.last(), 0.0001f)
    }

    @Test
    fun sixtyFpsRecordingAllowsSixtyZoomSubmissions() {
        val values = mutableListOf<Float>()
        val requests = LatestZoomRequests(60) { zoom, _ -> values += zoom }
        repeat(121) {
            requests.offer(1f + it / 120f)
            requests.flush(it * (1_000_000_000L / 120))
        }
        assertEquals(61, values.size)
    }
}
