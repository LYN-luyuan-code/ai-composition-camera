package com.luyuan.silkzoom

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.*

internal object FilterSheet {
    fun show(activity: Activity, filter: StreetFilter, strength: Float, onChanged: (StreetFilter, Float) -> Unit): AlertDialog {
        val theme = SoftTheme.from(activity)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        var selected = filter
        var amount = strength
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(24)); background = theme.shape(dp(24).toFloat(), theme.surface) }
        body.addView(TextView(activity).apply { text = "滤镜"; textSize = 20f; setTextColor(theme.ink) })
        val row = LinearLayout(activity)
        val controls = StreetFilter.entries.map { preset ->
            Button(activity).apply {
                isSelected = preset == filter; text = preset.label; isAllCaps = false; textSize = 13f; setTextColor(theme.ink); minWidth = 0; minHeight = dp(48)
                setPadding(dp(12), dp(6), dp(12), dp(6)); background = theme.control(dp(16).toFloat(), preset == filter)
            }.also { row.addView(it, LinearLayout.LayoutParams(dp(76), dp(52)).apply { marginEnd = dp(8) }) }
        }
        body.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(16) })
        val label = TextView(activity).apply { text = "强度 ${(amount * 100).toInt()}%"; textSize = 14f; setTextColor(theme.muted) }
        body.addView(label)
        val slider = SeekBar(activity).apply { max = 100; progress = (amount * 100).toInt(); contentDescription = "滤镜强度"; isEnabled = filter != StreetFilter.ORIGINAL }
        body.addView(slider, LinearLayout.LayoutParams(-1, dp(48)))
        controls.forEachIndexed { index, button ->
            button.setOnClickListener {
                selected = StreetFilter.entries[index]
                controls.forEachIndexed { i, view -> view.isSelected = i == index; view.background = theme.control(dp(16).toFloat(), i == index) }
                slider.isEnabled = selected != StreetFilter.ORIGINAL
                onChanged(selected, amount)
            }
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                amount = progress / 100f; label.text = "强度 $progress%"
                if (fromUser) onChanged(selected, amount)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        val done = Button(activity).apply { text = "完成"; isAllCaps = false; textSize = 14f; setTextColor(theme.accent); background = theme.control(dp(16).toFloat(), true) }
        body.addView(done, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val dialog = AlertDialog.Builder(activity).setView(body).create()
        done.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setGravity(Gravity.BOTTOM); setLayout(-1, -2); setDimAmount(.2f) }
        return dialog
    }
}
