package com.luyuan.silkzoom

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

internal data class SoftTheme(
    val surface: Int, val raised: Int, val ink: Int, val muted: Int,
    val accent: Int, val accentFill: Int, val guide: Int, val outline: Int,
) {
    fun shape(radius: Float, color: Int) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
    fun control(radius: Float, selected: Boolean = false): RippleDrawable {
        val fill = shape(radius, if (selected) accentFill else raised)
        return RippleDrawable(ColorStateList.valueOf(Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent))), fill, shape(radius, Color.WHITE))
    }
    companion object {
        fun camera() = SoftTheme(Color.BLACK, 0xB3202523.toInt(), Color.WHITE, 0xFFE2E7E4.toInt(),
            0xFFC3EAD2.toInt(), 0xEE345C47.toInt(), 0xFFC3EAD2.toInt(), 0xAAFFFFFF.toInt())
        fun from(context: Context): SoftTheme = if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            SoftTheme(0xFF202624.toInt(), 0xFF303835.toInt(), 0xFFF0F4F1.toInt(), 0xFFBBC8C1.toInt(),
                0xFFA9D5BD.toInt(), 0xFF3C5649.toInt(), 0xFFBDE4CC.toInt(), 0xFF708279.toInt())
        } else {
            SoftTheme(0xFFF1F4F1.toInt(), 0xFFFDFEFC.toInt(), 0xFF233C31.toInt(), 0xFF536A5D.toInt(),
                0xFF2E6951.toInt(), 0xFFDDECE1.toInt(), 0xFFBDE4CC.toInt(), 0xFF778C80.toInt())
        }
    }
}
