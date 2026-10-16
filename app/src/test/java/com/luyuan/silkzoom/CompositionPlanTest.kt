package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class CompositionPlanTest {
    @Test fun preCropTargetLandsAtAiTargetAfterCenteredZoom() {
        val plan = CompositionPlan(targetX = 0.7f, targetY = 0.4f)
        for (scale in listOf(1f, 1.12f, 1.25f, 2f, 3f, 5f, 8f)) {
            val before = plan.targetBeforeCrop(scale)
            assertEquals(plan.targetX, 0.5f + (before.first - 0.5f) * scale, 0.0001f)
            assertEquals(plan.targetY, 0.5f + (before.second - 0.5f) * scale, 0.0001f)
        }
    }

    @Test fun displayedCropFrameMapsLiveSubjectToFinalPosition() {
        val plan = CompositionPlan(targetX = 0.6f, targetY = 0.4f, cropScale = 3f).validated()
        val box = plan.cropFrame(0.42f, 0.47f, 3f)
        assertEquals(1f / 3f, box[2] - box[0], 0.0001f)
        assertEquals(1f / 3f, box[3] - box[1], 0.0001f)
        assertEquals(plan.targetX, (0.42f - box[0]) / (box[2] - box[0]), 0.0001f)
        assertEquals(plan.targetY, (0.47f - box[1]) / (box[3] - box[1]), 0.0001f)
        val aligned = plan.targetBeforeCrop(3f)
        val centered = plan.cropFrame(aligned.first, aligned.second, 3f)
        assertEquals(0.5f, (centered[0] + centered[2]) / 2, 0.0001f)
        assertEquals(0.5f, (centered[1] + centered[3]) / 2, 0.0001f)
    }

    @Test fun independentAspectCropMapsSubjectIntoFinalCoordinatesThroughEveryZoomStep() {
        for ((width, height) in listOf(.37f to 1f, 1f to .43f, .81f to .59f)) {
            val plan = CompositionPlan(targetX = .7f, targetY = .4f, cropWidth = width, cropHeight = height).validated()
            for (remaining in listOf(1f, 1.22f, 2f, 5f, 8f)) {
                val before = plan.targetBeforeCrop(remaining)
                assertEquals(plan.targetX, .5f + (before.first - .5f) * remaining / width, .00001f)
                assertEquals(plan.targetY, .5f + (before.second - .5f) * remaining / height, .00001f)
                val crop = plan.cropFrame(before.first, before.second, remaining)
                assertEquals(.5f, (crop[0] + crop[2]) / 2, .00001f)
                assertEquals(.5f, (crop[1] + crop[3]) / 2, .00001f)
                assertEquals(width / remaining, crop[2] - crop[0], .00001f)
                assertEquals(height / remaining, crop[3] - crop[1], .00001f)
            }
        }
    }

    @Test fun rejectsDegenerateAspectDimensions() {
        for (plan in listOf(CompositionPlan(cropWidth = 0f), CompositionPlan(cropHeight = 1.1f),
            CompositionPlan(cropWidth = Float.NaN), CompositionPlan(cropHeight = Float.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException::class.java) { plan.validated() }
        }
    }

    @Test fun validatesCoordinatesAndCropRatherThanFollowingInvalidAiOutput() {
        for (plan in listOf(CompositionPlan(subjectX = Float.NaN), CompositionPlan(targetY = 0.99f),
            CompositionPlan(cropScale = Float.POSITIVE_INFINITY), CompositionPlan(cropScale = 0.9f))) {
            assertThrows(IllegalArgumentException::class.java) { plan.validated() }
        }
    }

    @Test fun acceptsProviderBaseUrlsAndCompleteEndpoints() {
        for (provider in AiProvider.entries.filter { it != AiProvider.CUSTOM }) {
            val config = AiConfiguration(provider, provider.baseUrl, provider.defaultModel, "test")
            assertTrue(config.validated().endpoint().path.endsWith("/chat/completions"))
            assertEquals(config.endpoint(), config.copy(baseUrl = config.endpoint().toString()).endpoint())
        }
    }

    @Test fun rejectsInsecureOrCredentialBearingEndpoints() {
        val config = AiConfiguration(AiProvider.CUSTOM, "", "vision-model", "test")
        for (url in listOf("http://api.example.com/v1", "https://key@api.example.com/v1", "https://api.example.com?key=secret", "https://api.example.com/#fragment", "not a URL")) {
            assertThrows(IllegalArgumentException::class.java) { config.copy(baseUrl = url).validated() }
        }
        assertThrows(IllegalArgumentException::class.java) { config.copy(baseUrl = "https://api.example.com", apiKey = "test\r\nheader").validated() }
    }
}
