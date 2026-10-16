package com.luyuan.silkzoom

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PhotoSaver {
    fun save(context: Context, image: ImageProxy, zoom: Float, filter: StreetFilter, strength: Float, retainedWidth: Float = 1f, retainedHeight: Float = 1f) {
        var source: Bitmap? = null
        var output: Bitmap? = null
        try {
            source = image.toBitmap()
            val viewport = image.cropRect
            val crop = PhotoCrop.forDisplay(viewport.left, viewport.top, viewport.width(), viewport.height(), zoom,
                image.imageInfo.rotationDegrees, retainedWidth, retainedHeight)
            val rotated = image.imageInfo.rotationDegrees % 180 != 0
            output = Bitmap.createBitmap(if (rotated) crop.height else crop.width,
                if (rotated) crop.width else crop.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.translate(output.width / 2f, output.height / 2f)
            canvas.rotate(image.imageInfo.rotationDegrees.toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.matrix(strength)))
            }
            canvas.drawBitmap(source, Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
                RectF(-crop.width / 2f, -crop.height / 2f, crop.width / 2f, crop.height / 2f), paint)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Street_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/街拍相机")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建照片")
            try {
                resolver.openOutputStream(uri)?.use { stream -> check(output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) }
                    ?: error("无法写入照片")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
            } catch (failure: Throwable) {
                resolver.delete(uri, null, null)
                throw failure
            }
        } finally {
            source?.recycle()
            output?.recycle()
            image.close()
        }
    }

    fun cropPreview(source: Bitmap, zoom: Float): Bitmap {
        val crop = PhotoCrop.centered(0, 0, source.width, source.height, zoom)
        return Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
    }
}
