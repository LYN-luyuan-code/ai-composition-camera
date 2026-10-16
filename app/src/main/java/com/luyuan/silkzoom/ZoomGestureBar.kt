package com.luyuan.silkzoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A narrow right-edge zoom ruler with a full 48dp touch target and native range semantics. */
internal class ZoomGestureBar(context: Context) : View(context) {
    var onBegin: (() -> Unit)? = null
    var onChange: ((Float) -> Unit)? = null
    var onEnd: (() -> Unit)? = null
    private val theme = SoftTheme.camera()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = RectF()
    private val density = resources.displayMetrics.density
    private var minZoom = 1f
    private var maxZoom = 8f
    private var zoom = 1f
    private var downY = 0f
    private var downFraction = 0f
    private var dragging = false
    private var active = false
    private var pointer = MotionEvent.INVALID_POINTER_ID
    private val railTop get() = dp(45f)
    private val railBottom get() = max(railTop + dp(12f), height - dp(30f))

    init { isFocusable = true; isClickable = true; contentDescription = "变焦，向上滑动放大，向下滑动缩小" }
    fun setRange(min: Float, max: Float) { minZoom = min; maxZoom = max; invalidate() }
    fun setZoom(value: Float) {
        zoom = value.coerceIn(minZoom, maxZoom)
        ViewCompat.setStateDescription(this, String.format(Locale.US, "%.1f 倍", zoom))
        invalidate()
    }
    fun setActive(value: Boolean) { if (active != value) { active = value; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val x = width / 2f
        panel.set(dp(4f), 0f, width - dp(4f), height.toFloat())
        paint.style = Paint.Style.FILL; paint.color = if (active) 0xAA000000.toInt() else 0x44000000
        canvas.drawRoundRect(panel, dp(20f), dp(20f), paint)
        if (isFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.color = theme.accent
            canvas.drawRoundRect(panel, dp(20f), dp(20f), paint); paint.style = Paint.Style.FILL
        }
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = dp(1.5f); paint.color = theme.ink
        canvas.drawLine(x - dp(4f), dp(15f), x + dp(4f), dp(15f), paint)
        canvas.drawLine(x, dp(11f), x, dp(19f), paint)
        canvas.drawLine(x - dp(4f), height - dp(14f), x + dp(4f), height - dp(14f), paint)
        paint.textSize = 11f * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "%.1f×", zoom), x, dp(35f), paint)
        paint.color = theme.outline; paint.strokeWidth = dp(2f)
        canvas.drawLine(x, railTop, x, railBottom, paint)
        for (ratio in listOf(1f, 2f, 4f, 8f)) if (ratio in minZoom..maxZoom) {
            val y = railBottom - ZoomScale.fraction(ratio, minZoom, maxZoom) * (railBottom - railTop)
            canvas.drawLine(x - dp(4f), y, x + dp(4f), y, paint)
        }
        val thumb = railBottom - ZoomScale.fraction(zoom, minZoom, maxZoom) * (railBottom - railTop)
        paint.color = theme.accent; paint.strokeWidth = dp(if (active) 3f else 2f)
        canvas.drawLine(x, thumb, x, railBottom, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, thumb, dp(if (active) 6f else 4.5f), paint)
        paint.color = theme.raised
        canvas.drawCircle(x, thumb, dp(1.5f), paint)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled) return super.onKeyDown(keyCode, event)
        val desired = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> zoom * 1.1f
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MINUS -> zoom / 1.1f
            KeyEvent.KEYCODE_MOVE_HOME -> minZoom
            KeyEvent.KEYCODE_MOVE_END -> maxZoom
            else -> return super.onKeyDown(keyCode, event)
        }
        onBegin?.invoke(); onChange?.invoke(desired.coerceIn(minZoom, maxZoom)); onEnd?.invoke(); return true
    }
    private fun changeAt(y: Float) {
        onChange?.invoke(ZoomScale.value(downFraction + (downY - y) / (railBottom - railTop), minZoom, maxZoom))
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0); dragging = true
                downY = event.y; downFraction = ZoomScale.fraction(zoom, minZoom, maxZoom)
                parent.requestDisallowInterceptTouchEvent(true)
                onBegin?.invoke(); setActive(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (dragging && index >= 0) changeAt(event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) finish()
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    if (abs(event.y - downY) < ViewConfiguration.get(context).scaledTouchSlop) {
                        if (event.y < railTop) onChange?.invoke((zoom * 1.15f).coerceIn(minZoom, maxZoom))
                        else if (event.y > railBottom) onChange?.invoke((zoom / 1.15f).coerceIn(minZoom, maxZoom))
                    } else changeAt(event.y)
                    super.performClick()
                }
                finish()
            }
            MotionEvent.ACTION_CANCEL -> finish()
        }
        return true
    }
    private fun finish() {
        if (dragging) { dragging = false; onEnd?.invoke() }
        pointer = MotionEvent.INVALID_POINTER_ID; setActive(false)
        parent.requestDisallowInterceptTouchEvent(false)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, minZoom, maxZoom, zoom)
        if (isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return false
        val desired = when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> arguments?.takeIf { it.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) }
                ?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> zoom * 1.1f
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> zoom / 1.1f
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (!desired.isFinite()) return false
        onBegin?.invoke(); onChange?.invoke(desired.coerceIn(minZoom, maxZoom)); onEnd?.invoke()
        return true
    }
    private fun dp(value: Float) = value * density
}
