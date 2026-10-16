package com.luyuan.silkzoom

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.core.view.ViewCompat
import kotlin.math.*

/** Camera-backed LUT cards render off the UI thread; shutter placement is independent. */
internal class CameraFilterStrip(context: Context) : LinearLayout(context) {
    var onChanged: ((StreetFilter, Float) -> Unit)? = null
    var onInfo: ((StreetFilter) -> Unit)? = null
    private val theme=SoftTheme.camera()
    private val density=resources.displayMetrics.density
    private var source:Bitmap?=null
    private var filter=StreetFilter.ORIGINAL
    private var amount=1f
    private var interactive=true
    private var category="全部"
    private var catalog=StreetFilter.entries
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var generation=0
    private var released=false
    private val row=LinearLayout(context).apply { setPadding(dp(20),0,dp(20),0) }
    private var cards=listOf<FilterCard>()
    private val scroll=HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false;addView(row) }
    private val ruler=StrengthRuler()
    private val refreshVisible=Runnable { source?.let { renderCards(it) } }
    init {
        orientation=VERTICAL
        scroll.setOnScrollChangeListener { _,_,_,_,_ -> removeCallbacks(refreshVisible);postDelayed(refreshVisible,60L) }
        addView(scroll,LayoutParams(-1,dp(92)));addView(ruler,LayoutParams(-1,dp(56)))
        rebuild();update(filter,amount)
    }
    fun setCatalog(filters:List<StreetFilter>) { catalog=filters;rebuild() }
    fun setCategory(value:String) {
        category=value.takeIf { it in listOf("全部","色彩","胶片","单色","我的LUT") } ?: "全部"
        rebuild()
    }
    private fun rebuild() {
        generation++;row.removeAllViews()
        val visible=catalog.filter { category=="全部" || it.group==category || it===StreetFilter.ORIGINAL }
        cards=visible.map { preset -> FilterCard(preset).also { card ->
            card.setOnClickListener { update(preset,amount);onChanged?.invoke(filter,amount) }
            card.setOnLongClickListener { onInfo?.invoke(preset);true }
            row.addView(card,LayoutParams(dp(76),dp(92)).apply { marginEnd=dp(12) })
        } }
        update(filter,amount)
        scroll.post { cards.firstOrNull { it.isSelected }?.let { scroll.scrollTo(it.left-dp(20),0) } }
        removeCallbacks(refreshVisible);post(refreshVisible)
    }
    fun setPreview(bitmap:Bitmap?) { val previous=source;source=bitmap;if(previous!==bitmap)previous?.recycle();generation++;if(bitmap!=null)renderCards(bitmap) }
    private fun renderCards(bitmap:Bitmap) {
        if(released)return
        val key=++generation;val snapshot=bitmap.copy(Bitmap.Config.ARGB_8888,false)
        // Paint the selected and on-screen cards first, rather than every off-screen preset.
        val targets=cards.filter { it.preset.name==filter.name ||
            (it.right>scroll.scrollX-dp(88) && it.left<scroll.scrollX+scroll.width+dp(88))
        }.sortedBy { card -> when {
            card.preset.name==filter.name -> 0
            card.right>scroll.scrollX && card.left<scroll.scrollX+scroll.width -> 1
            else -> 2
        } }
        worker.execute {
            try {
                for(card in targets) {
                    if(key!=generation || Thread.currentThread().isInterrupted)break
                    val result=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(result);val scale=max(96f/snapshot.width,96f/snapshot.height)
                    canvas.drawBitmap(snapshot,null,RectF(48-snapshot.width*scale/2,48-snapshot.height*scale/2,48+snapshot.width*scale/2,48+snapshot.height*scale/2),Paint(Paint.FILTER_BITMAP_FLAG))
                    val valid=runCatching { LutBitmap.applyInPlace(result,card.preset,1f) }.isSuccess
                    post { if(key!=generation || released || !valid)result.recycle() else { card.preview=result;card.invalidate() } }
                }
            } finally { snapshot.recycle() }
        }
    }
    fun update(preset:StreetFilter,strength:Float) {
        val changed=filter.name!=preset.name
        filter=preset;amount=strength
        if(changed)scroll.post { cards.firstOrNull { it.preset.name==preset.name }?.let { card -> if(card.left<scroll.scrollX || card.right>scroll.scrollX+scroll.width)scroll.smoothScrollTo(card.left-dp(20),0) } }
        cards.forEach { it.isSelected=it.preset.name==filter.name;it.isEnabled=interactive;it.invalidate() }
        ruler.isEnabled=interactive && filter!==StreetFilter.ORIGINAL;ruler.alpha=if(ruler.isEnabled)1f else .45f
        ViewCompat.setStateDescription(ruler,"${(amount*100).roundToInt()}%");ruler.invalidate()
    }
    fun setInteractive(enabled:Boolean) { interactive=enabled;update(filter,amount) }
    fun release() { released=true;generation++;removeCallbacks(refreshVisible);worker.shutdownNow();source?.recycle();source=null }
    private fun dp(value:Int)=(value*density).roundToInt()
    private inner class FilterCard(val preset:StreetFilter):View(context) {
        var preview:Bitmap?=null
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val box=RectF()
        private val label=if(preset.label.length>5)preset.label.take(4)+"…" else preset.label
        init { isClickable=true;isFocusable=true;contentDescription="${preset.label}滤镜，长按查看说明" }
        override fun onDraw(canvas:Canvas) {
            box.set(3f*density,3f*density,width-3f*density,67f*density)
            paint.style=Paint.Style.FILL;paint.color=0xFF171717.toInt();canvas.drawRoundRect(box,2f*density,2f*density,paint)
            preview?.let { canvas.drawBitmap(it,null,box,paint) }
            if(isSelected){paint.style=Paint.Style.STROKE;paint.strokeWidth=2f*density;paint.color=Color.WHITE;canvas.drawRoundRect(box,2f*density,2f*density,paint)}
            paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=13f*resources.displayMetrics.scaledDensity;paint.color=Color.WHITE
            canvas.drawText(label,width/2f,86f*density,paint)
            if(isFocused){paint.style=Paint.Style.STROKE;paint.strokeWidth=2f*density;paint.color=theme.accent;canvas.drawRoundRect(density,density,width-density,height-density,4f*density,4f*density,paint)}
        }
        override fun onFocusChanged(gainFocus:Boolean,direction:Int,previouslyFocusedRect:Rect?) { super.onFocusChanged(gainFocus,direction,previouslyFocusedRect);invalidate() }
        override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.Button";info.isSelected=isSelected}
    }

    private inner class StrengthRuler : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var initial = 0f
        init { isClickable = true; isFocusable = true; contentDescription = "滤镜强度，左右滑动调整" }
        override fun onDraw(canvas: Canvas) {
            val center = width / 2f; val y = 42f * density
            paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            for (step in 0..50) {
                val x = center + (step * .02f - amount) * width * .65f
                if (x !in 12f * density..width - 12f * density) continue
                paint.color = 0xFF8D8D8D.toInt()
                val length = if (step % 5 == 0) 14f else 9f
                canvas.drawLine(x, y - length * density / 2, x, y + length * density / 2, paint)
            }
            paint.color = theme.accent; paint.strokeWidth = 2f * density
            canvas.drawLine(center, y - 12f * density, center, y + 12f * density, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14f * resources.displayMetrics.scaledDensity
            canvas.drawText((amount * 100).roundToInt().toString(), center, 20f * density, paint)
            if (isFocused) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density; paint.color = theme.accent
                canvas.drawRoundRect(2f * density, density, width - 2f * density, height - density, 6f * density, 6f * density, paint)
            }
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
        }
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (!isEnabled) return super.onKeyDown(keyCode, event)
            val desired = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> amount - .02f
                KeyEvent.KEYCODE_DPAD_RIGHT -> amount + .02f
                KeyEvent.KEYCODE_MOVE_HOME -> 0f
                KeyEvent.KEYCODE_MOVE_END -> 1f
                else -> return super.onKeyDown(keyCode, event)
            }
            change(desired); return true
        }
        private fun change(value: Float) { amount = value.coerceIn(0f, 1f); update(filter, amount); onChanged?.invoke(filter, amount) }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; initial = amount; parent.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> change(initial + (downX - event.x) / (width * .65f))
                MotionEvent.ACTION_UP -> { change(initial + (downX - event.x) / (width * .65f)); performClick(); parent.requestDisallowInterceptTouchEvent(false) }
                MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info); info.className = "android.widget.SeekBar"
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, amount * 100)
            if (isEnabled) { info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS); info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        }
        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            if (!isEnabled) return false
            val desired = when (action) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> arguments?.takeIf { it.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) }?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.div(100f) ?: return false
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> amount + .05f
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> amount - .05f
                else -> return super.performAccessibilityAction(action, arguments)
            }
            if (!desired.isFinite()) return false
            change(desired); return true
        }
    }
}
