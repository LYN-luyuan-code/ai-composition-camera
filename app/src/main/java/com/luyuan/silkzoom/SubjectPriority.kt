package com.luyuan.silkzoom

/** Subjective photographic priorities, not object size or an objective image-quality score. */
internal object SubjectPriority {
    fun score(art: Float, story: Float, geometry: Float, clarity: Float): Float {
        require(listOf(art, story, geometry, clarity).all { it.isFinite() && it in 0f..1f }) {
            "AI 返回的主体权重无效，请重新寻找"
        }
        return .45f * art + .25f * story + .20f * geometry + .10f * clarity
    }
    fun sameScene(a: IntArray, b: IntArray): Boolean {
        if (a.isEmpty() || a.size != b.size) return false
        fun luma(c: Int) = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) / 256f
        val indices = a.indices.step(13)
        val meanA = indices.map { luma(a[it]) }.average().toFloat()
        val meanB = indices.map { luma(b[it]) }.average().toFloat()
        return indices.map { kotlin.math.abs(luma(a[it]) - meanA - luma(b[it]) + meanB) }.average() < 7.5
    }
}
