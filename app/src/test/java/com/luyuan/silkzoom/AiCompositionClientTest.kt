package com.luyuan.silkzoom

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

class AiCompositionClientTest {
    private val plan = """{"subject_x":0.5,"subject_y":0.45,"target_x":0.66,"target_y":0.33,"crop_scale":1.18,"reason":"给视线留白"}"""
    private fun response(content: String = plan): String = JSONObject().put("choices",
        org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
    private fun config(provider: AiProvider) = AiConfiguration(provider, provider.baseUrl, provider.defaultModel, "test-key")

    private fun userContent(request: JSONObject): org.json.JSONArray {
        val messages = request.getJSONArray("messages")
        return (0 until messages.length()).map { messages.getJSONObject(it) }
            .single { it.getString("role") == "user" }.getJSONArray("content")
    }

    @Test fun deepseekAndMimoSendRealImageRequestsToTheirOwnEndpoints() {
        for (provider in listOf(AiProvider.DEEPSEEK, AiProvider.MIMO)) {
            val fake = FakeConnection(config(provider).endpoint(), response())
            val client = AiCompositionClient { url -> assertEquals(config(provider).endpoint(), url); fake }
            val result = client.analyze(config(provider), byteArrayOf(1, 2, 3))
            assertEquals(0.66f, result.targetX, 0.001f)
            assertEquals("POST", fake.requestMethod)
            assertEquals("Bearer test-key", fake.getRequestProperty("Authorization"))
            assertFalse(fake.instanceFollowRedirects)
            assertTrue(fake.disconnected)
            val request = JSONObject(fake.sent.toString("UTF-8"))
            assertEquals(provider.defaultModel, request.getString("model"))
            val content = userContent(request)
            assertEquals("data:image/jpeg;base64,AQID", content.getJSONObject(0).getJSONObject("image_url").getString("url"))
            assertFalse(request.toString().contains("test-key"))
            assertFalse(request.getBoolean("stream"))
        }
    }

    @Test fun usesShortNonThinkingRequestsAndPreservesSelectedSubject() {
        for (provider in listOf(AiProvider.DEEPSEEK, AiProvider.MIMO)) {
            val request = AiCompositionClient.buildRequest(config(provider), byteArrayOf(1), 0.25f to 0.4f)
            assertEquals("disabled", request.getJSONObject("thinking").getString("type"))
            val limit = if (provider == AiProvider.MIMO) "max_completion_tokens" else "max_tokens"
            assertEquals(384, request.getInt(limit))
            val prompt = userContent(request).getJSONObject(1).getString("text")
            assertTrue(prompt.contains("x=0.25, y=0.4"))
        }
    }

    @Test fun allowsMeaningfulLargeCropWithinCameraCapabilities() {
        assertEquals(3f, AiCompositionClient.parseResponse(response(plan.replace("1.18", "3.0"))).cropScale, 0.001f)
        val request = AiCompositionClient.buildRequest(config(AiProvider.DEEPSEEK), byteArrayOf(1), 0.5f to 0.4f, 4.5f)
        val prompt = userContent(request).getJSONObject(1).getString("text")
        assertTrue(prompt.contains("range 1..4.5"))
        assertTrue(prompt.contains("clutter"))
    }

    @Test fun parsesFencedJsonAndRejectsInvalidGeometryOrMissingSubject() {
        assertEquals(1.18f, AiCompositionClient.parseResponse(response("```json\n$plan\n```" )).cropScale, 0.001f)
        for (content in listOf(plan.replace("0.66", "8"), plan.replace("1.18", "20.0"), "{\"error\":\"没有主体\"}", "not JSON")) {
            assertThrows(Exception::class.java) { AiCompositionClient.parseResponse(response(content)) }
        }
    }

    @Test fun reportsStatusWithoutShowingProviderErrorBodyOrSecrets() {
        for (status in listOf(400, 401, 403, 404, 429, 500, 302)) {
            val fake = FakeConnection(config(AiProvider.DEEPSEEK).endpoint(), "sensitive-server-body", status)
            val error = assertThrows(AiCompositionException::class.java) {
                AiCompositionClient { fake }.analyze(config(AiProvider.DEEPSEEK), byteArrayOf(1))
            }
            assertFalse(error.message!!.contains("sensitive-server-body"))
            assertTrue(fake.disconnected)
        }
    }

    @Test fun cancellationBeforeStartingNeverSendsAnImage() {
        var opened = false
        val client = AiCompositionClient { opened = true; throw AssertionError() }
        client.cancel()
        assertThrows(CancellationException::class.java) { client.analyze(config(AiProvider.MIMO), byteArrayOf(1)) }
        assertFalse(opened)
    }

    @Test fun capsResponseSize() {
        val fake = FakeConnection(config(AiProvider.MIMO).endpoint(), "x".repeat(256 * 1024 + 1))
        assertThrows(AiCompositionException::class.java) { AiCompositionClient { fake }.analyze(config(AiProvider.MIMO), byteArrayOf(1)) }
        assertTrue(fake.disconnected)
    }

    @Test fun acceptsIndependentCropExtentsAndDerivesZoomWithoutSnappingRatio() {
        for ((width, height) in listOf(.72f to .31f, .29f to .83f, 1f to .35f, .125f to .071f)) {
            val json = JSONObject(plan).put("crop_width", width).put("crop_height", height)
                .put("crop_scale", 7f) // Free-crop extents are authoritative, not a conflicting legacy scalar.
            val parsed = AiCompositionClient.parseResponse(response(json.toString()))
            assertEquals(width, parsed.cropWidth / parsed.cropScale, .00001f)
            assertEquals(height, parsed.cropHeight / parsed.cropScale, .00001f)
            assertEquals(1f, maxOf(parsed.cropWidth, parsed.cropHeight), .00001f)
        }
    }

    @Test fun rejectsIncompleteOrOutOfBoundsFreeCrops() {
        for (json in listOf(JSONObject(plan).put("crop_width", .6),
            JSONObject(plan).put("crop_width", 0).put("crop_height", .5),
            JSONObject(plan).put("crop_width", 1.1).put("crop_height", .5),
            JSONObject(plan).put("crop_width", .05).put("crop_height", .06))) {
            assertThrows(Exception::class.java) { AiCompositionClient.parseResponse(response(json.toString())) }
        }
    }

    @Test fun promptSeparatesPhotographicJudgmentFromExecutableFreeCropGeometry() {
        val request = AiCompositionClient.buildRequest(config(AiProvider.MIMO), byteArrayOf(1))
        val messages = request.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val prompt = userContent(request).getJSONObject(1).getString("text")
        assertTrue(prompt.contains("ANY aspect ratio"))
        assertTrue(prompt.contains("crop_width") && prompt.contains("crop_height"))
    }


    @Test fun automaticallyChoosesHighestArtisticPriorityRatherThanCenterOrSize() {
        val ordinary = JSONObject(plan).put("subject_label", "中央建筑").put("art", .2).put("story", .2).put("geometry", .3).put("clarity", 1)
        val expressive = JSONObject(plan).put("subject_x", .72).put("subject_label", "窗边的倒影")
            .put("art", .95).put("story", .8).put("geometry", .9).put("clarity", .7)
        val result = AiCompositionClient.parseResponse(response(JSONObject().put("candidates", org.json.JSONArray().put(ordinary).put(expressive)).toString()))
        assertEquals("窗边的倒影", result.subjectLabel)
        assertEquals(.72f, result.subjectX, .0001f)
        assertTrue(result.subjectScore > .8f)
        val request = AiCompositionClient.buildRequest(config(AiProvider.MIMO), byteArrayOf(1))
        assertEquals(640, request.getInt("max_completion_tokens"))
        assertTrue(userContent(request).getJSONObject(1).getString("text").contains("Automatically discover"))
    }

    @Test fun invalidCandidateCannotWinOrHideAnOtherwiseValidCandidate() {
        val valid = JSONObject(plan).put("art", .6).put("story", .5).put("geometry", .5).put("clarity", .8)
        val invalid = JSONObject(valid.toString()).put("art", 9)
        val result = AiCompositionClient.parseResponse(response(JSONObject().put("candidates", org.json.JSONArray().put(invalid).put(valid)).toString()))
        assertEquals(.66f, result.targetX, .001f)
        assertThrows(IllegalArgumentException::class.java) {
            AiCompositionClient.parseResponse(response(JSONObject().put("candidates", org.json.JSONArray().put(invalid)).toString()))
        }
    }

    private class FakeConnection(url: URL, private val body: String, private val status: Int = 200) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getResponseCode() = status
    }
}
