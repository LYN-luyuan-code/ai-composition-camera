package com.luyuan.silkzoom

/** Coordinates refer to the displayed preview, including front-camera mirroring. */
internal data class CompositionPlan(
    val subjectX: Float = 0.5f,
    val subjectY: Float = 0.5f,
    val targetX: Float = 1f / 3f,
    val targetY: Float = 1f / 3f,
    val cropScale: Float = 1.12f,
    val reason: String = "三分法构图",
    // Independent retained fractions of the final zoomed viewfinder; no aspect-ratio preset.
    val cropWidth: Float = 1f,
    val cropHeight: Float = 1f,
    val subjectLabel: String = "",
    val subjectScore: Float = 0f,
) {
    fun validated(): CompositionPlan {
        require(subjectX.isFinite() && subjectX in 0.1f..0.9f && subjectY.isFinite() && subjectY in 0.1f..0.9f) {
            "AI 返回的主体位置无法跟踪，请重新构图"
        }
        require(targetX.isFinite() && targetX in 0.2f..0.8f && targetY.isFinite() && targetY in 0.2f..0.8f) {
            "AI 返回的目标位置无效，请重试"
        }
        require(cropScale.isFinite() && cropScale in 1f..8f) { "AI 返回的裁切幅度无效，请重试" }
        require(cropWidth.isFinite() && cropWidth > 0f && cropWidth <= 1f &&
            cropHeight.isFinite() && cropHeight > 0f && cropHeight <= 1f) { "AI 返回的自由裁切区域无效，请重试" }
        return copy(reason = reason.replace(Regex("[\\p{Cntrl}]"), " ").take(48),
            subjectLabel = subjectLabel.replace(Regex("[\\p{Cntrl}]"), " ").take(12))
    }

    fun cropFrame(subjectX: Float, subjectY: Float, remainingScale: Float): FloatArray {
        val scale = remainingScale.coerceAtLeast(1f)
        val halfWidth = cropWidth * 0.5f / scale
        val halfHeight = cropHeight * 0.5f / scale
        val centerX = subjectX - (targetX - 0.5f) * cropWidth / scale
        val centerY = subjectY - (targetY - 0.5f) * cropHeight / scale
        return floatArrayOf(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    companion object {
        /** Correct only the display's own zoom since a tracking sample; do not infer phone motion. */
        fun mapZoomPoint(x: Float, y: Float, fromZoom: Float, toZoom: Float): Pair<Float, Float> {
            val scale = toZoom / fromZoom.coerceAtLeast(.1f)
            return (.5f + (x - .5f) * scale) to (.5f + (y - .5f) * scale)
        }
    }

    fun targetBeforeCrop(actualScale: Float): Pair<Float, Float> =
        (0.5f + (targetX - 0.5f) * cropWidth / actualScale.coerceAtLeast(1f)) to (0.5f + (targetY - 0.5f) * cropHeight / actualScale.coerceAtLeast(1f))
}
