package com.luyuan.silkzoom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.util.Locale
import kotlin.math.*

/** Full-screen viewfinder with compact native controls floating at its edges. */
internal class CameraOverlayView(context: Context) : FrameLayout(context) {
    interface Listener {
        fun onCapture()
        fun onAutoCompose()
        fun onEndComposition()
        fun onSettings()
        fun onFilters()
        fun onCycleFlash()
        fun onZoomGestureStart()
        fun onZoomGestureEnd()
        fun onZoomTarget(zoomRatio: Float)
        fun onLensStopSelected(zoomRatio: Float)
        fun onResolutionSelected(preset: ResolutionPreset)
        fun onTapToFocus(x: Float, y: Float)
        fun onPreviewAreaChanged(left: Int, top: Int, width: Int, height: Int)
    }
    var listener: Listener? = null
    val previewBounds = RectF()
    val guideRadius: Float get() = dp(20f)
    private val theme = SoftTheme.camera()
    private val density = resources.displayMetrics.density
    private var topInset = 0
    private var bottomInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var enabledControls = false
    private var zoom = 1f
    private var minZoom = 1f
    private var maxZoom = 8f
    private var selectedFilter = StreetFilter.ORIGINAL
    private var filterStrength = 1f
    private var compositionActive = false
    private var searching = false
    private var cropping = false
    private var ready = false
    private var subject: Pair<Float, Float>? = null
    private var target = .5f to .5f
    private var chosenSubject: Pair<Float, Float>? = null
    private var goalZoom: Float? = null
    private var cropFrame: FloatArray? = null
    private var retainedPhotoWidth = 1f
    private var retainedPhotoHeight = 1f
    private var reason: String? = null
    private var transientMessage: String? = null
    private var messageGeneration = 0
    private var focusPoint: Pair<Float, Float>? = null
    private var focusUntil = 0L
    private var flashUntil = 0L
    private var saving = false
    private val guide = GuideView(context)
    private val topBar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val bottomBar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8f).toInt(), 0, dp(8f).toInt(), 0) }
    private val status = TextView(context).apply {
        textSize = 13f; setTextColor(theme.muted); gravity = Gravity.CENTER; maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        minHeight = dp(32f).toInt()
        background = theme.shape(dp(12f), theme.raised)
        setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt())
        setShadowLayer(dp(3f), 0f, dp(1f), Color.BLACK)
    }
    private val caption = TextView(context).apply { background = theme.shape(dp(12f), theme.raised); setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0); textSize = 12f; setTextColor(theme.muted); gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val flashButton = button("闪光 关") { listener?.onCycleFlash() }
    private val qualityButton = button("标准") { showQualityMenu() }
    private val settingsButton = ImageButton(context).apply {
        background = theme.control(dp(16f)); setImageDrawable(Icon("settings", theme.ink)); contentDescription = "AI 供应商设置"
        setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        setOnClickListener { listener?.onSettings() }
    }
    private val filterButton = button("滤镜") { listener?.onFilters() }.apply {
        setCompoundDrawablesWithIntrinsicBounds(null, Icon("filter", theme.ink), null, null)
        compoundDrawablePadding = dp(4f).toInt()
    }
    private val composeButton = button("AI 构图") {
        if (compositionActive) listener?.onEndComposition() else listener?.onAutoCompose()
    }.apply {
        contentDescription = "开始 AI 构图"
        setCompoundDrawablesWithIntrinsicBounds(null, Icon("compose", theme.ink), null, null)
        compoundDrawablePadding = dp(4f).toInt()
    }
    private val shutter = ImageButton(context).apply {
        background = theme.shape(dp(44f), Color.TRANSPARENT); setImageDrawable(Icon("shutter", Color.WHITE)); contentDescription = "拍照"
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(10f).toInt(), dp(10f).toInt(), dp(10f).toInt(), dp(10f).toInt())
        elevation = 0f; stateListAnimator = null
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); listener?.onCapture() }
    }
    private val zoomRow = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val zoomButtons = listOf(1f, 1.5f, 2f, 3f).map { ratio ->
        ratio to button(if (ratio == floor(ratio)) "${ratio.toInt()}×" else "1.5×") { listener?.onLensStopSelected(ratio) }
    }
    private var actionSizing = 0
    private val zoomGestureBar = ZoomGestureBar(context).apply {
        onBegin = { beginZoomGesture() }
        onChange = { listener?.onZoomTarget(it) }
        onEnd = { endZoomGesture() }
    }
    private val actions = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val allControls: List<View> get() = listOf(flashButton, qualityButton, settingsButton, filterButton, composeButton, shutter, zoomGestureBar) + zoomButtons.map { it.second }

    init {
        clipChildren = false
        addView(guide, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        topBar.addView(flashButton, LinearLayout.LayoutParams(dp(72f).toInt(), dp(48f).toInt()))
        topBar.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(qualityButton, LinearLayout.LayoutParams(dp(64f).toInt(), dp(48f).toInt()))
        topBar.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(settingsButton, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()))
        addView(topBar)
        zoomButtons.forEachIndexed { i, (_, control) -> zoomRow.addView(control, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()).apply { if (i > 0) marginStart = dp(8f).toInt() }) }
        bottomBar.addView(zoomRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48f).toInt()))
        bottomBar.addView(status, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f).toInt(); bottomMargin = dp(8f).toInt() })
        actions.addView(filterButton)
        actions.addView(shutter)
        actions.addView(composeButton)
        bottomBar.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(80f).toInt()))
        bottomBar.addView(caption, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(24f).toInt()).apply { topMargin = dp(4f).toInt() })
        addView(bottomBar)
        addView(zoomGestureBar)
        setControlsEnabled(false)
        setFilter(StreetFilter.ORIGINAL, 1f)
        updateStatus()
    }

    private fun button(label: String, click: () -> Unit) = Button(context).apply {
        text = label; textSize = 12f; isAllCaps = false; minWidth = 0; minHeight = 0
        gravity = Gravity.CENTER; setTextColor(theme.ink); background = theme.control(dp(16f)); stateListAnimator = null
        setPadding(dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt())
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); click() }
    }

    fun setSafeInsets(top: Int, bottom: Int, left: Int, right: Int) { topInset = top; bottomInset = bottom; leftInset = left; rightInset = right; requestLayout() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        val landscape = w > h
        val rail = if (landscape) min(dp(264f).toInt(), (w * .45f).toInt()) else 0
        val controlWidth = if (landscape) rail - dp(16f).toInt() else min(w - dp(32f).toInt() - leftInset - rightInset, dp(520f).toInt())
        val side = dp(64f).toInt()
        val center = dp(80f).toInt()
        if (actionSizing != side) {
            actionSizing = side
            actions.getChildAt(0).layoutParams = LinearLayout.LayoutParams(side, dp(64f).toInt()).apply { marginEnd = dp(12f).toInt() }
            actions.getChildAt(1).layoutParams = LinearLayout.LayoutParams(center, center)
            actions.getChildAt(2).layoutParams = LinearLayout.LayoutParams(side, dp(64f).toInt()).apply { marginStart = dp(12f).toInt() }
        }
        topBar.measure(MeasureSpec.makeMeasureSpec(w - rail - dp(32f).toInt() - leftInset - rightInset, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(64f).toInt(), MeasureSpec.EXACTLY))
        bottomBar.measure(MeasureSpec.makeMeasureSpec(controlWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h - topInset - bottomInset, MeasureSpec.AT_MOST))
        zoomGestureBar.measure(MeasureSpec.makeMeasureSpec(dp(48f).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(min(dp(204f).toInt(), (h - topInset - bottomInset - dp(100f)).toInt()).coerceAtLeast(dp(120f).toInt()), MeasureSpec.EXACTLY))
        guide.measure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = width; val h = height; val landscape = w > h
        val rail = if (landscape) min(dp(264f).toInt(), (w * .45f).toInt()) else 0
        guide.layout(0, 0, w, h)
        val gutter = dp(16f).toInt()
        topBar.layout(leftInset + gutter, topInset + dp(4f).toInt(), w - rightInset - rail - gutter, topInset + dp(68f).toInt())
        val controlsX = if (landscape) w - rightInset - rail + (rail - bottomBar.measuredWidth) / 2 else (w - bottomBar.measuredWidth) / 2
        val controlsY = if (landscape) topInset + (h - topInset - bottomInset - bottomBar.measuredHeight) / 2 else h - bottomInset - bottomBar.measuredHeight - dp(8f).toInt()
        bottomBar.layout(controlsX, controlsY, controlsX + bottomBar.measuredWidth, controlsY + bottomBar.measuredHeight)
        val barX = w - rightInset - rail - dp(56f).toInt()
        val barMinY = topBar.bottom + dp(8f).toInt()
        val barMaxY = (if (landscape) h - bottomInset - dp(12f).toInt() else controlsY - dp(12f).toInt()) - zoomGestureBar.measuredHeight
        val barY = ((h - zoomGestureBar.measuredHeight) / 2).coerceIn(barMinY, max(barMinY, barMaxY))
        zoomGestureBar.layout(barX, barY, barX + zoomGestureBar.measuredWidth, barY + zoomGestureBar.measuredHeight)
        val next = RectF(0f, 0f, w.toFloat(), h.toFloat())
        if (previewBounds != next) {
            previewBounds.set(next)
            listener?.onPreviewAreaChanged(0, 0, w, h)
        }
    }

    fun setZoomRange(min: Float, max: Float) { minZoom = min; maxZoom = max; zoomGestureBar.setRange(min, max) }
    fun setZoom(value: Float) {
        zoom = value
        zoomGestureBar.setZoom(value)
        zoomButtons.forEach { (ratio, button) ->
            val selected = abs(ln(value / ratio)) < .04f
            if (button.isSelected != selected) {
                button.isSelected = selected
                button.background = theme.control(dp(16f), selected)
                button.setTextColor(if (selected) theme.accent else theme.ink)
            }
        }
        updateCaption()
    }
    fun setFlashLabel(label: String) { flashButton.text = "闪光 $label"; flashButton.contentDescription = "闪光灯$label" }
    fun setResolutionPreset(preset: ResolutionPreset) { qualityButton.text = preset.label; qualityButton.contentDescription = "照片画质，${preset.label}" }
    fun setFilter(filter: StreetFilter, strength: Float) { selectedFilter = filter; filterStrength = strength; filterButton.isSelected = filter != StreetFilter.ORIGINAL; updateCaption() }
    fun setZoomAnimating(active: Boolean) { zoomGestureBar.setActive(active) }
    private fun beginZoomGesture() { listener?.onZoomGestureStart(); zoomGestureBar.setActive(true) }
    private fun endZoomGesture() { listener?.onZoomGestureEnd(); zoomGestureBar.setActive(false) }
    fun setPhotoCrop(width: Float, height: Float) {
        if (retainedPhotoWidth == width && retainedPhotoHeight == height) return
        retainedPhotoWidth = width; retainedPhotoHeight = height
        updateCaption(); guide.invalidate()
    }
    fun setControlsEnabled(enabled: Boolean) { enabledControls = enabled; allControls.forEach { it.isEnabled = enabled && !saving; it.alpha = if (it.isEnabled) 1f else .45f } }
    fun setSaving(value: Boolean) { saving = value; setControlsEnabled(enabledControls); updateStatus() }
    fun setSelectedSubject(point: Pair<Float, Float>) { chosenSubject = point; guide.invalidate() }
    fun clearSelectedSubject() { chosenSubject = null; guide.invalidate() }
    fun setComposition(active: Boolean, subject: Pair<Float, Float>? = null, target: Pair<Float, Float> = .5f to .5f,
        ready: Boolean = false, analyzing: Boolean = false, reason: String? = null, cropping: Boolean = false,
        cropApplied: Boolean = false, cropFrame: FloatArray? = null, goalZoom: Float? = null) {
        if (ready && !this.ready) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val activeChanged = compositionActive != active
        compositionActive = active; this.subject = subject; this.target = target; this.ready = ready
        searching = analyzing; this.reason = reason; this.cropping = cropping; this.cropFrame = cropFrame; this.goalZoom = goalZoom
        if (activeChanged) {
            composeButton.text = if (active) "结束" else "AI 构图"
            composeButton.contentDescription = if (active) "结束 AI 构图" else "开始 AI 构图"
            composeButton.isSelected = active
            composeButton.background = theme.control(dp(16f), active)
        }
        updateStatus(); guide.invalidate()
    }
    fun showMessage(text: String, durationMillis: Long = 3_000L) {
        transientMessage = text; val generation = ++messageGeneration; updateStatus()
        postDelayed({ if (generation == messageGeneration) { transientMessage = null; updateStatus() } }, durationMillis)
    }
    fun showFocus(x: Float, y: Float) { focusPoint = x to y; focusUntil = SystemClock.uptimeMillis() + 550; guide.invalidate() }
    fun showCaptureFlash() { flashUntil = SystemClock.uptimeMillis() + 100; guide.invalidate() }
    private fun updateCaption() {
        caption.text = String.format(Locale.US, "%.1f×", zoom) + "  ·  " + selectedFilter.label +
            (if (selectedFilter == StreetFilter.ORIGINAL) "" else " ${(filterStrength * 100).roundToInt()}%") +
            (if (retainedPhotoWidth < .999f || retainedPhotoHeight < .999f) "  ·  自由裁切" else "")
    }
    private fun updateStatus() {
        val text = transientMessage ?: when {
            saving -> "正在保存照片…"
            searching -> "正在寻找有表现力的主体…"
            ready -> "构图就绪，按快门拍摄"
            cropping -> "正在收紧画面…"
            compositionActive && subject == null -> "暂时跟丢，请保持取景或重新寻找"
            compositionActive -> "沿箭头轻移手机" + (reason?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
            else -> ""
        }
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (status.text.toString() != text) status.text = text
    }
    private fun showQualityMenu() {
        PopupMenu(context, qualityButton).apply {
            ResolutionPreset.entries.forEach { menu.add(it.label).setOnMenuItemClickListener { _ -> listener?.onResolutionSelected(it); true } }
            show()
        }
    }
    private fun dp(value: Float) = value * density

    private inner class GuideView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Path()
        private var downX = 0f; private var downY = 0f; private var anchor = 1f
        private var dragging = false; private var pinching = false; private var pinchTarget = 1f
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { if (!enabledControls || saving) return false; pinching = true; pinchTarget = zoom; beginZoomGesture(); return true }
            override fun onScale(detector: ScaleGestureDetector): Boolean { pinchTarget = ZoomCurve.fromPinch(pinchTarget, detector.scaleFactor, minZoom, maxZoom); listener?.onZoomTarget(pinchTarget); return true }
            override fun onScaleEnd(detector: ScaleGestureDetector) { endZoomGesture() }
        })
        init { contentDescription = "取景画面，轻点对焦，双指缩放或上下滑动变焦"; isClickable = true }
        override fun onDraw(canvas: Canvas) {
            val bounds = previewBounds
            if (bounds.isEmpty) return
            val now = SystemClock.uptimeMillis()
            canvas.save(); canvas.clipRect(bounds)
            fun point(p: Pair<Float, Float>) = PointF(bounds.left + p.first * bounds.width(), bounds.top + p.second * bounds.height())
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1.5f); paint.strokeCap = Paint.Cap.ROUND
            val retainedFrame = if (!searching && compositionActive) cropFrame else null
            val visibleFrame = retainedFrame ?: if (retainedPhotoWidth < .999f || retainedPhotoHeight < .999f)
                floatArrayOf(.5f - retainedPhotoWidth / 2f, .5f - retainedPhotoHeight / 2f,
                    .5f + retainedPhotoWidth / 2f, .5f + retainedPhotoHeight / 2f) else null
            visibleFrame?.let { frame ->
                val rect = RectF(bounds.left + frame[0] * bounds.width(), bounds.top + frame[1] * bounds.height(),
                    bounds.left + frame[2] * bounds.width(), bounds.top + frame[3] * bounds.height())
                // Thin outline plus corner marks defines the saved area without darkening the viewfinder.
                paint.color = Color.argb(100, 8, 27, 19); paint.strokeWidth = dp(3f)
                canvas.drawRect(rect, paint)
                paint.color = theme.guide; paint.strokeWidth = dp(1f); canvas.drawRect(rect, paint)
                val corner = min(dp(12f), min(rect.width(), rect.height()) / 4f)
                paint.strokeWidth = dp(2f)
                for (x in listOf(rect.left, rect.right)) for (y in listOf(rect.top, rect.bottom)) {
                    canvas.drawLine(x, y, x + if (x == rect.left) corner else -corner, y, paint)
                    canvas.drawLine(x, y, x, y + if (y == rect.top) corner else -corner, paint)
                }
            }
            paint.strokeWidth = dp(1.5f)
            if (searching) {
                val p = point(subject ?: chosenSubject ?: (.5f to .5f))
                val radius = dp(24f)
                paint.color = theme.guide
                val animate = ValueAnimator.areAnimatorsEnabled()
                val angle = if (animate) now % 1800L / 1800f * 360f else -90f
                canvas.drawArc(p.x - radius, p.y - radius, p.x + radius, p.y + radius, angle, 70f, false, paint)
                paint.color = Color.argb(150, 255, 255, 255)
                canvas.drawArc(p.x - radius, p.y - radius, p.x + radius, p.y + radius, angle + 180f, 70f, false, paint)
                if (animate) postInvalidateOnAnimation()
            } else if (compositionActive && subject != null) {
                val start = PointF(bounds.centerX(), bounds.centerY())
                // Moving the camera right moves the subject left; the arrow describes PHONE motion.
                val dx = subject!!.first - target.first; val dy = subject!!.second - target.second
                val end = point((.5f + dx).coerceIn(.1f, .9f) to (.5f + dy).coerceIn(.1f, .9f))
                val distance = hypot(end.x - start.x, end.y - start.y)
                val color = if (ready) Color.rgb(166, 228, 189) else theme.guide
                if (distance > guideRadius + dp(10f)) {
                    val ux = (end.x - start.x) / distance; val uy = (end.y - start.y) / distance
                    val finish = PointF(end.x - ux * guideRadius, end.y - uy * guideRadius)
                    line.reset(); line.moveTo(start.x + ux * dp(9f), start.y + uy * dp(9f))
                    line.lineTo(finish.x, finish.y)
                    paint.color = Color.argb(45, 8, 27, 19); paint.strokeWidth = dp(11f)
                    canvas.drawPath(line, paint)
                    paint.color = Color.argb(185, Color.red(color), Color.green(color), Color.blue(color))
                    paint.strokeWidth = dp(8f); canvas.drawPath(line, paint)
                    val size = dp(14f)
                    canvas.drawLine(finish.x, finish.y, finish.x - ux * size - uy * size * .6f,
                        finish.y - uy * size + ux * size * .6f, paint)
                    canvas.drawLine(finish.x, finish.y, finish.x - ux * size + uy * size * .6f,
                        finish.y - uy * size - ux * size * .6f, paint)
                }
                paint.color = Color.argb(100, 8, 27, 19); paint.strokeWidth = dp(4f); canvas.drawCircle(end.x, end.y, guideRadius, paint)
                paint.color = color; paint.strokeWidth = dp(1.8f); canvas.drawCircle(end.x, end.y, guideRadius, paint)
                paint.color = Color.WHITE; canvas.drawCircle(start.x, start.y, dp(4f), paint)
                if (ready) { line.reset(); line.moveTo(start.x - dp(7f), start.y); line.lineTo(start.x - dp(2f), start.y + dp(5f)); line.lineTo(start.x + dp(8f), start.y - dp(6f)); paint.color = color; canvas.drawPath(line, paint) }
            } else chosenSubject?.let { val p = point(it); paint.color = Color.WHITE; canvas.drawCircle(p.x, p.y, dp(10f), paint) }
            if (focusUntil > now) focusPoint?.let { p -> paint.color = Color.WHITE; paint.strokeWidth = dp(1f); canvas.drawRoundRect(p.first - dp(20f), p.second - dp(20f), p.first + dp(20f), p.second + dp(20f), dp(6f), dp(6f), paint); postInvalidateOnAnimation() }
            if (flashUntil > now) { canvas.drawColor(Color.argb(50, 255, 255, 255)); postInvalidateOnAnimation() }
            canvas.restore()
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!enabledControls || saving) return false
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !previewBounds.contains(event.x, event.y)) return false
            pinch.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; anchor = zoom; dragging = false; pinching = false }
                MotionEvent.ACTION_MOVE -> if (!pinch.isInProgress && !pinching) {
                    val delta = downY - event.y
                    if (!dragging && abs(delta) > slop * 1.5f) { dragging = true; beginZoomGesture() }
                    if (dragging) listener?.onZoomTarget(ZoomCurve.fromDrag(anchor, delta, previewBounds.height() * .6f, minZoom, maxZoom))
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) endZoomGesture()
                    else if (!pinching && hypot(event.x - downX, event.y - downY) < slop * 1.5f) { showFocus(event.x, event.y); listener?.onTapToFocus(event.x, event.y); super.performClick() }
                }
                MotionEvent.ACTION_CANCEL -> if (dragging || pinching) endZoomGesture()
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); if (enabledControls && !saving) listener?.onTapToFocus(previewBounds.centerX(), previewBounds.centerY()); return true }
    }

    private inner class Icon(private val kind: String, private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = this@Icon.color }
        override fun draw(canvas: Canvas) {
            canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            paint.strokeWidth = if (kind == "shutter") 1.25f else 1.7f
            when (kind) {
                "settings" -> { canvas.drawCircle(12f, 12f, 6.5f, paint); canvas.drawCircle(12f, 12f, 2.3f, paint); for (i in 0..7) { val a = i * PI / 4; canvas.drawLine(12f + cos(a).toFloat() * 7, 12f + sin(a).toFloat() * 7, 12f + cos(a).toFloat() * 9, 12f + sin(a).toFloat() * 9, paint) } }
                "filter" -> { canvas.drawCircle(9f, 9f, 5f, paint); canvas.drawCircle(15f, 9f, 5f, paint); canvas.drawCircle(12f, 15f, 5f, paint) }
                "compose" -> { canvas.drawCircle(12f, 12f, 8f, paint); val p = Path(); p.moveTo(15f, 8f); p.lineTo(12f, 15f); p.lineTo(9f, 11f); p.close(); canvas.drawPath(p, paint) }
                "shutter" -> { canvas.drawCircle(12f, 12f, 10f, paint); paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 12f, 7.5f, paint); paint.style = Paint.Style.STROKE }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = dp(24f).toInt()
        override fun getIntrinsicHeight() = dp(24f).toInt()
    }
}
