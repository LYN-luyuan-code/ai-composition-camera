package com.luyuan.silkzoom

import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy

/** Copies only the Y plane; tracking never needs a full-size preview screenshot. */
internal class CompositionFrames {
    private data class Frame(val width: Int, val height: Int, val luma: ByteArray, val sensorToBuffer: Matrix, val time: Long)
    @Volatile private var latest: Frame? = null
    private var lastCopy = 0L

    fun clear() { latest = null }

    fun offer(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCopy < 60L) return
        lastCopy = now
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val offset = buffer.position()
        val bytes = ByteArray(image.width * image.height)
        for (y in 0 until image.height) {
            if (plane.pixelStride == 1) {
                buffer.position(offset + y * plane.rowStride)
                buffer.get(bytes, y * image.width, image.width)
            } else for (x in 0 until image.width) {
                bytes[y * image.width + x] = buffer.get(offset + y * plane.rowStride + x * plane.pixelStride)
            }
        }
        latest = Frame(image.width, image.height, bytes, Matrix(image.imageInfo.sensorToBufferTransformMatrix), now)
    }

    fun pixels(sensorToView: Matrix?, viewWidth: Int, viewHeight: Int, width: Int, height: Int): IntArray? {
        val frame = latest ?: return null
        if (SystemClock.elapsedRealtime() - frame.time > 400L || viewWidth == 0 || viewHeight == 0 || sensorToView == null) return null
        val viewToBuffer = Matrix()
        if (!sensorToView.invert(viewToBuffer)) return null
        viewToBuffer.postConcat(frame.sensorToBuffer)
        val matrix = FloatArray(9)
        viewToBuffer.getValues(matrix)
        return IntArray(width * height) { index ->
            val x = (index % width + 0.5f) * viewWidth / width
            val y = (index / width + 0.5f) * viewHeight / height
            val mappedX = (matrix[0] * x + matrix[1] * y + matrix[2]).coerceIn(0f, frame.width - 1.01f)
            val mappedY = (matrix[3] * x + matrix[4] * y + matrix[5]).coerceIn(0f, frame.height - 1.01f)
            val bx = mappedX.toInt(); val by = mappedY.toInt()
            val fx = mappedX - bx; val fy = mappedY - by
            val row = by * frame.width + bx
            val top = (frame.luma[row].toInt() and 255) * (1 - fx) + (frame.luma[row + 1].toInt() and 255) * fx
            val bottom = (frame.luma[row + frame.width].toInt() and 255) * (1 - fx) +
                (frame.luma[row + frame.width + 1].toInt() and 255) * fx
            val value = (top * (1 - fy) + bottom * fy).toInt()
            (value shl 16) or (value shl 8) or value
        }
    }
}
