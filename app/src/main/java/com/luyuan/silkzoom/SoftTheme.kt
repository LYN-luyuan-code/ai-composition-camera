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
        fun camera() = SoftTheme(Color.BLACK, 0xFF242424.toInt(), Color.WHITE, 0xFFE5E5E5.toInt(),
            0xFFFF3B3B.toInt(), 0x332E2E2E, Color.WHITE, 0xAAFFFFFF.toInt())
        fun from(context: Context): SoftTheme = if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            SoftTheme(0xFF202020.toInt(), 0xFF303030.toInt(), 0xFFF4F4F4.toInt(), 0xFFC6C6C6.toInt(),
                0xFFFF6B6B.toInt(), 0xFF543535.toInt(), 0xFFE6E6E6.toInt(), 0xFF808080.toInt())
        } else {
            SoftTheme(0xFFF3F3F3.toInt(), 0xFFFEFEFE.toInt(), 0xFF242424.toInt(), 0xFF616161.toInt(),
                0xFFC62828.toInt(), 0xFFF4DEDE.toInt(), 0xFFE6E6E6.toInt(), 0xFF858585.toInt())
        }
    }
}
