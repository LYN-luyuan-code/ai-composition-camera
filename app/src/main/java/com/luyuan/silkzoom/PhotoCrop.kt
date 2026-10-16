package com.luyuan.silkzoom

internal data class PhotoCrop(val left: Int, val top: Int, val width: Int, val height: Int) {
    companion object {
        fun forDisplay(left: Int, top: Int, width: Int, height: Int, zoom: Float,
            rotationDegrees: Int, retainedWidth: Float, retainedHeight: Float): PhotoCrop {
            val swapsAxes = rotationDegrees % 180 != 0
            return centered(left, top, width, height, zoom,
                if (swapsAxes) retainedHeight else retainedWidth,
                if (swapsAxes) retainedWidth else retainedHeight)
        }

        fun centered(left: Int, top: Int, width: Int, height: Int, zoom: Float, retainedWidth: Float = 1f, retainedHeight: Float = 1f): PhotoCrop {
            require(retainedWidth.isFinite() && retainedWidth > 0f && retainedWidth <= 1f)
            require(retainedHeight.isFinite() && retainedHeight > 0f && retainedHeight <= 1f)
            val ratio = zoom.coerceAtLeast(1f)
            val w = (width / ratio * retainedWidth).toInt().coerceIn(1, width)
            val h = (height / ratio * retainedHeight).toInt().coerceIn(1, height)
            return PhotoCrop(left + (width - w) / 2, top + (height - h) / 2, w, h)
        }
    }
}
