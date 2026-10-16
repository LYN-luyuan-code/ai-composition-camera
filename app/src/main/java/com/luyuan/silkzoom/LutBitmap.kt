package com.luyuan.silkzoom

import android.graphics.Bitmap

internal object LutBitmap {
    fun applyInPlace(bitmap: Bitmap, filter: StreetFilter, strength: Float) {
        if(filter===StreetFilter.ORIGINAL || strength<=0f)return
        val lut=filter.lut
        val pixels=IntArray(bitmap.width*minOf(32,bitmap.height))
        for(y in 0 until bitmap.height step 32) {
            val rows=minOf(32,bitmap.height-y);val count=bitmap.width*rows
            bitmap.getPixels(pixels,0,bitmap.width,0,y,bitmap.width,rows)
            for(i in 0 until count)pixels[i]=lut.color(pixels[i],strength)
            bitmap.setPixels(pixels,0,bitmap.width,0,y,bitmap.width,rows)
        }
    }
}
