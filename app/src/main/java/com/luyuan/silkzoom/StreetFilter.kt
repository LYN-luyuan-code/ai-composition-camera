package com.luyuan.silkzoom

/** The same matrix drives the live preview and the saved JPEG. */
enum class StreetFilter(val label: String, private val transform: FloatArray) {
    ORIGINAL("原片", filterIdentity()),
    SOFT("柔和", floatArrayOf(.88f,.04f,.03f,0f,12f, .03f,.91f,.03f,0f,10f, .03f,.04f,.88f,0f,13f, 0f,0f,0f,1f,0f)),
    WARM("暖调", floatArrayOf(1.04f,.02f,0f,0f,9f, 0f,1f,0f,0f,4f, 0f,0f,.92f,0f,-3f, 0f,0f,0f,1f,0f)),
    COOL("冷调", floatArrayOf(.94f,0f,0f,0f,-2f, 0f,1f,.02f,0f,3f, 0f,.02f,1.05f,0f,6f, 0f,0f,0f,1f,0f)),
    MONO("黑白", floatArrayOf(.213f,.715f,.072f,0f,0f, .213f,.715f,.072f,0f,0f, .213f,.715f,.072f,0f,0f, 0f,0f,0f,1f,0f));

    fun matrix(strength: Float): FloatArray {
        val original = filterIdentity()
        val amount = strength.coerceIn(0f, 1f)
        return FloatArray(20) { original[it] + (transform[it] - original[it]) * amount }
    }

 }

private fun filterIdentity() = floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)
