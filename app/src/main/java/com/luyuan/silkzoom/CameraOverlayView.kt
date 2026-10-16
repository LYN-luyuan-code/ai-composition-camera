package com.luyuan.silkzoom

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.util.Locale
import kotlin.math.*

/** A full-screen camera surface following the user's native camera references. */
internal class CameraOverlayView(context: Context) : FrameLayout(context) {
    interface Listener {
        fun onCapture()
        fun onAutoCompose()
        fun onEndComposition()
        fun onSettings()
        fun onFilters()
        fun onFilterInfo(filter: StreetFilter)
        fun onFilterChanged(filter: StreetFilter, strength: Float)
        fun onOpenPhoto()
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
    private var topInset = 0; private var bottomInset = 0; private var leftInset = 0; private var rightInset = 0
    private var enabledControls = false
    private var zoom = 1f; private var minZoom = 1f; private var maxZoom = 8f
    private var selectedFilter = StreetFilter.ORIGINAL; private var filterStrength = 1f
    private var compositionActive = false; private var searching = false; private var cropping = false; private var ready = false
    private var subject: Pair<Float, Float>? = null
    private var target = .5f to .5f
    private var chosenSubject: Pair<Float, Float>? = null
    private var goalZoom: Float? = null; private var cropFrame: FloatArray? = null
    private var retainedPhotoWidth = 1f; private var retainedPhotoHeight = 1f
    private var reason: String? = null; private var transientMessage: String? = null; private var messageGeneration = 0
    private var focusPoint: Pair<Float, Float>? = null; private var focusUntil = 0L; private var flashUntil = 0L
    private var saving = false; private var gridVisible = true
    var filtersVisible = false
        private set
    private val guide = GuideView(context)
    private val shade = object : View(context) {
        private val paint = Paint()
        private var topShader: Shader? = null; private var bottomShader: Shader? = null
        private var topEdge = 0f; private var bottomEdge = 0f
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            topEdge = min(h * .18f, dp(136f)); bottomEdge = h - min(h * .46f, dp(380f))
            topShader = LinearGradient(0f, 0f, 0f, topEdge, 0xF5000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            bottomShader = LinearGradient(0f, bottomEdge, 0f, h.toFloat(), intArrayOf(Color.TRANSPARENT, 0xC9000000.toInt(), 0xF5000000.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        }
        override fun onDraw(canvas: Canvas) {
            paint.shader = topShader; canvas.drawRect(0f, 0f, width.toFloat(), topEdge, paint)
            paint.shader = bottomShader; canvas.drawRect(0f, bottomEdge, width.toFloat(), height.toFloat(), paint); paint.shader = null
        }
    }
    private val status = TextView(context).apply {
        textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
        background = theme.shape(dp(8f), 0x99000000.toInt())
    }
    private val topBar = FrameLayout(context)
    private val options = LinearLayout(context).apply { gravity = Gravity.CENTER; visibility = GONE; background = theme.shape(dp(12f), 0xE6121212.toInt()) }
    private val flashButton = iconButton("flash-off", "闪光灯关") { listener?.onCycleFlash() }
    private val expandButton = iconButton("down", "展开相机选项") { setOptionsVisible(options.visibility != VISIBLE) }.apply { background = android.graphics.drawable.InsetDrawable(theme.control(dp(18f)), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt(), dp(10f).toInt()) }
    private val filterButton = iconButton("filter", "打开滤镜") { listener?.onFilters() }
    private val settingsButton = iconButton("settings", "相机设置") { listener?.onSettings() }
    private val qualityButton = button("标准") { showQualityMenu() }
    private val gridButton = button("参考线 开") { gridVisible = !gridVisible; updateGridLabel(); guide.invalidate() }
    private val photoButton = button("拍照") { closePanels() }.apply { textSize = 15f; setTextColor(theme.accent) }
    private val filterTab = button("滤镜") { listener?.onFilters() }.apply { textSize = 15f }
    private val modes = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val actions = FrameLayout(context)
    private val photoThumbnail = iconButton("gallery", "查看最近照片") { listener?.onOpenPhoto() }.apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
        background = bareRipple()
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: View, outline: Outline) { val inset = dp(6f).toInt(); outline.setRoundRect(inset, inset, view.width - inset, view.height - inset, dp(3f)) } }
    }
    private val composeButton = button("AI 构图") {
        if (compositionActive) listener?.onEndComposition() else { closePanels(); listener?.onAutoCompose() }
    }.apply {
        textSize = 11f; contentDescription = "开始 AI 构图"
        setCompoundDrawablesWithIntrinsicBounds(null, Icon("compose", theme.ink), null, null); compoundDrawablePadding = dp(5f).toInt()
    }
    private val shutter = iconButton("shutter", "拍照") { listener?.onCapture() }.apply { setPadding(0, 0, 0, 0) }
    private val zoomRow = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val zoomButtons = listOf(1f, 1.5f, 2f, 3f).map { ratio ->
        ratio to button(if (ratio == floor(ratio)) "${ratio.toInt()}×" else "1.5×") { listener?.onLensStopSelected(ratio) }.apply { textSize = 15f }
    }
    private val zoomGestureBar = ZoomGestureBar(context).apply {
        onBegin = { beginZoomGesture() }; onChange = { listener?.onZoomTarget(it) }; onEnd = { endZoomGesture() }
    }
    private val filterStrip = CameraFilterStrip(context).apply {
        visibility = GONE; onChanged = { filter, strength -> listener?.onFilterChanged(filter, strength) }; onInfo = { listener?.onFilterInfo(it) }
    }
    private val allControls: List<View> get() = listOf(flashButton, expandButton, qualityButton, gridButton, settingsButton, filterButton, photoButton, filterTab, photoThumbnail, composeButton, shutter, zoomGestureBar) + zoomButtons.map { it.second }

    init {
        clipChildren = false
        addView(shade); addView(guide)
        listOf(flashButton, expandButton, filterButton, settingsButton).forEach { topBar.addView(it, LayoutParams(dp(48f).toInt(), dp(48f).toInt())) }
        addView(topBar)
        options.addView(qualityButton, LinearLayout.LayoutParams(0, dp(48f).toInt(), 1f))
        options.addView(gridButton, LinearLayout.LayoutParams(0, dp(48f).toInt(), 1f)); addView(options)
        zoomButtons.forEach { (_, control) -> zoomRow.addView(control, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() }) }
        addView(zoomRow)
        modes.addView(photoButton, LinearLayout.LayoutParams(dp(72f).toInt(), dp(48f).toInt()))
        modes.addView(filterTab, LinearLayout.LayoutParams(dp(72f).toInt(), dp(48f).toInt()).apply { marginStart = dp(16f).toInt() }); addView(modes)
        actions.addView(photoThumbnail, LayoutParams(dp(48f).toInt(), dp(48f).toInt())); actions.addView(shutter, LayoutParams(dp(80f).toInt(), dp(80f).toInt())); actions.addView(composeButton, LayoutParams(dp(56f).toInt(), dp(64f).toInt())); addView(actions)
        addView(filterStrip); addView(status); addView(zoomGestureBar)
        updateGridLabel(); setControlsEnabled(false); setFilter(StreetFilter.ORIGINAL, 1f); updateStatus()
    }
    private fun button(label: String, click: () -> Unit) = Button(context).apply {
        text = label; textSize = 13f; isAllCaps = false; minWidth = 0; minHeight = 0
        gravity = Gravity.CENTER; setTextColor(theme.ink); background = bareRipple(); stateListAnimator = null; setShadowLayer(dp(2f), 0f, dp(1f), Color.BLACK)
        setPadding(dp(4f).toInt(), 0, dp(4f).toInt(), 0)
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); click() }
    }
    private fun bareRipple() = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, theme.shape(dp(24f), Color.WHITE))
    private fun iconButton(kind: String, label: String, click: () -> Unit) = ImageButton(context).apply {
        background = bareRipple(); setImageDrawable(Icon(kind, Color.WHITE)); contentDescription = label; stateListAnimator = null
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); click() }
    }
    fun setSafeInsets(top: Int, bottom: Int, left: Int, right: Int) { topInset = top; bottomInset = bottom; leftInset = left; rightInset = right; requestLayout() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        val rail = if (w > h) min(dp(248f).toInt(), (w * .4f).toInt()) else 0
        val availableWidth = w - leftInset - rightInset
        val controlsWidth = if (rail > 0) rail else min(availableWidth - dp(24f).toInt(), dp(480f).toInt())
        fun measure(view: View, width: Int, height: Int) { view.measure(MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height.coerceAtLeast(1), MeasureSpec.EXACTLY)) }
        measure(shade, w, h); measure(guide, w, h); measure(topBar, availableWidth - rail, dp(56f).toInt())
        measure(options, min(availableWidth - rail - dp(24f).toInt(), dp(320f).toInt()), dp(56f).toInt())
        measure(actions, controlsWidth, dp(96f).toInt())
        measure(zoomRow, controlsWidth, dp(48f).toInt()); measure(modes, controlsWidth, dp(48f).toInt())
        val filterWidth = if (rail > 0) min(availableWidth - rail - dp(16f).toInt(), dp(440f).toInt()) else min(availableWidth, dp(520f).toInt())
        measure(filterStrip, filterWidth, dp(148f).toInt())
        status.measure(MeasureSpec.makeMeasureSpec(controlsWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(66f).toInt(), MeasureSpec.AT_MOST))
        measure(zoomGestureBar, dp(48f).toInt(), min(dp(168f).toInt(), h - topInset - bottomInset - dp(130f).toInt()).coerceAtLeast(dp(112f).toInt()))
        setMeasuredDimension(w, h)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = width; val h = height; val landscape = w > h
        val rail = if (landscape) min(dp(248f).toInt(), (w * .4f).toInt()) else 0
        shade.layout(0, 0, w, h); guide.layout(0, 0, w, h)
        val topY = topInset + dp(12f).toInt(); topBar.layout(leftInset, topY, w - rightInset - rail, topY + topBar.measuredHeight)
        val s = dp(48f).toInt(); val tw = topBar.width
        flashButton.layout(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt() + s, dp(4f).toInt() + s)
        expandButton.layout((tw - s) / 2, dp(4f).toInt(), (tw + s) / 2, dp(4f).toInt() + s)
        filterButton.layout(tw - dp(112f).toInt(), dp(4f).toInt(), tw - dp(64f).toInt(), dp(4f).toInt() + s)
        settingsButton.layout(tw - dp(56f).toInt(), dp(4f).toInt(), tw - dp(8f).toInt(), dp(4f).toInt() + s)
        options.layout(leftInset + (tw - options.measuredWidth) / 2, topBar.bottom + dp(8f).toInt(), leftInset + (tw + options.measuredWidth) / 2, topBar.bottom + dp(64f).toInt())
        val actionX = if (landscape) w - rightInset - rail else leftInset + (w - leftInset - rightInset - actions.measuredWidth) / 2
        val actionY = if (landscape) topInset + (h - topInset - bottomInset - actions.measuredHeight) / 2 + dp(28f).toInt() else h - bottomInset - dp(128f).toInt()
        actions.layout(actionX, actionY, actionX + actions.measuredWidth, actionY + actions.measuredHeight)
        val aw = actions.width; val shutterSide = dp(80f).toInt(); val thumbSide = dp(48f).toInt(); val aiSide = dp(56f).toInt()
        shutter.layout((aw - shutterSide) / 2, dp(8f).toInt(), (aw + shutterSide) / 2, dp(8f).toInt() + shutterSide)
        photoThumbnail.layout(dp(12f).toInt(), dp(24f).toInt(), dp(12f).toInt() + thumbSide, dp(24f).toInt() + thumbSide)
        composeButton.layout(aw - aiSide - dp(8f).toInt(), dp(16f).toInt(), aw - dp(8f).toInt(), dp(16f).toInt() + dp(64f).toInt())
        val normalModeY = actions.top - dp(60f).toInt()
        val stripX = if (landscape) leftInset + (w - leftInset - rightInset - rail - filterStrip.measuredWidth) / 2 else leftInset + (w - leftInset - rightInset - filterStrip.measuredWidth) / 2
        val stripY = if (landscape) h - bottomInset - filterStrip.measuredHeight - dp(16f).toInt() else actions.top - filterStrip.measuredHeight - dp(16f).toInt()
        filterStrip.layout(stripX, stripY, stripX + filterStrip.measuredWidth, stripY + filterStrip.measuredHeight)
        val modeX = if (filtersVisible && landscape) stripX else actionX
        val modeY = if (filtersVisible) stripY - dp(48f).toInt() else normalModeY
        val modeWidth = if (filtersVisible && landscape) filterStrip.measuredWidth else modes.measuredWidth
        modes.layout(modeX, modeY, modeX + modeWidth, modeY + modes.measuredHeight)
        zoomRow.layout(actionX, normalModeY - dp(56f).toInt(), actionX + zoomRow.measuredWidth, normalModeY - dp(8f).toInt())
        val messageX = if (filtersVisible && landscape) stripX else actionX
        val messageY = (if (filtersVisible) modeY else zoomRow.top) - status.measuredHeight - dp(8f).toInt()
        status.layout(messageX + (modes.width - status.measuredWidth) / 2, messageY, messageX + (modes.width + status.measuredWidth) / 2, messageY + status.measuredHeight)
        val barX = w - rightInset - rail - dp(52f).toInt()
        val barMinY = topBar.bottom + dp(72f).toInt()
        val barMaxY = (if (filtersVisible) filterStrip.top - dp(60f).toInt() else if (landscape) h - bottomInset - dp(12f).toInt() else zoomRow.top - dp(12f).toInt()) - zoomGestureBar.measuredHeight
        val barY = ((h - zoomGestureBar.measuredHeight) / 2).coerceIn(barMinY, max(barMinY, barMaxY))
        zoomGestureBar.layout(barX, barY, barX + zoomGestureBar.measuredWidth, barY + zoomGestureBar.measuredHeight)
        val next = RectF(0f, 0f, w.toFloat(), h.toFloat())
        if (previewBounds != next) { previewBounds.set(next); listener?.onPreviewAreaChanged(0, 0, w, h) }
    }
    fun setFilterCategory(category:String) { filterStrip.setCategory(category) }
    fun setFilterCatalog(filters:List<StreetFilter>) { filterStrip.setCatalog(filters) }
    fun setFilterPreview(bitmap:Bitmap) { filterStrip.setPreview(bitmap) }
    fun openFilters(preview: Bitmap?) {
        setOptionsVisible(false); filtersVisible = true; filterStrip.setPreview(preview); filterStrip.visibility = VISIBLE
        zoomRow.visibility = GONE; zoomGestureBar.visibility = if (width > height) GONE else VISIBLE; filterTab.setTextColor(theme.accent); photoButton.setTextColor(Color.WHITE)
        filterButton.contentDescription = "收起滤镜"; requestLayout()
        if (ValueAnimator.areAnimatorsEnabled()) { filterStrip.alpha = 0f; filterStrip.animate().alpha(1f).setDuration(160).start() }
    }
    fun closePanels(): Boolean {
        val wasOpen = filtersVisible || options.visibility == VISIBLE
        if (filtersVisible) {
            filtersVisible = false; filterStrip.animate().cancel(); filterStrip.alpha = 1f; filterStrip.visibility = GONE
            zoomRow.visibility = VISIBLE; zoomGestureBar.visibility = VISIBLE; photoButton.setTextColor(theme.accent); filterTab.setTextColor(Color.WHITE); filterButton.contentDescription = "打开滤镜"; requestLayout()
        }
        setOptionsVisible(false); return wasOpen
    }
    private fun setOptionsVisible(visible: Boolean) {
        options.visibility = if (visible) VISIBLE else GONE
        expandButton.setImageDrawable(Icon(if (visible) "up" else "down", Color.WHITE))
        expandButton.contentDescription = if (visible) "收起相机选项" else "展开相机选项"
    }
    private fun updateGridLabel() { gridButton.text = "参考线 ${if (gridVisible) "开" else "关"}"; gridButton.isSelected = gridVisible; gridButton.setTextColor(if (gridVisible) theme.accent else Color.WHITE) }
    fun setZoomRange(min: Float, max: Float) { minZoom = min; maxZoom = max; zoomGestureBar.setRange(min, max) }
    fun setZoom(value: Float) {
        zoom = value; zoomGestureBar.setZoom(value)
        zoomButtons.forEach { (ratio, button) -> val selected = abs(ln(value / ratio)) < .04f; if (button.isSelected != selected) { button.isSelected = selected; button.setTextColor(if (selected) theme.accent else Color.WHITE) } }
    }
    fun setFlashLabel(label: String) { flashButton.setImageDrawable(Icon(if (label == "关") "flash-off" else "flash", if (label == "关") Color.WHITE else theme.accent)); flashButton.contentDescription = "闪光灯$label" }
    fun setResolutionPreset(preset: ResolutionPreset) { qualityButton.text = preset.label; qualityButton.contentDescription = "照片画质，${preset.label}" }
    fun setFilter(filter: StreetFilter, strength: Float) { selectedFilter = filter; filterStrength = strength; filterStrip.update(filter, strength); filterTab.contentDescription = "滤镜，${filter.label}，强度 ${(strength * 100).roundToInt()}%" }
    fun setPhotoThumbnail(bitmap: Bitmap) { photoThumbnail.setImageBitmap(bitmap); photoThumbnail.setPadding(dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt()) }
    fun setZoomAnimating(active: Boolean) { zoomGestureBar.setActive(active) }
    private fun beginZoomGesture() { listener?.onZoomGestureStart(); zoomGestureBar.setActive(true) }
    private fun endZoomGesture() { listener?.onZoomGestureEnd(); zoomGestureBar.setActive(false) }
    fun setPhotoCrop(width: Float, height: Float) { if (retainedPhotoWidth != width || retainedPhotoHeight != height) { retainedPhotoWidth = width; retainedPhotoHeight = height; guide.invalidate() } }
    fun setControlsEnabled(enabled: Boolean) { enabledControls = enabled; allControls.forEach { it.isEnabled = enabled && !saving; it.alpha = if (it.isEnabled) 1f else .45f }; filterStrip.setInteractive(enabled && !saving) }
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
        if (activeChanged) { composeButton.text = if (active) "结束" else "AI 构图"; composeButton.contentDescription = if (active) "结束 AI 构图" else "开始 AI 构图"; composeButton.isSelected = active; composeButton.setTextColor(if (active) theme.accent else Color.WHITE) }
        updateStatus(); guide.invalidate()
    }
    fun showMessage(text: String, durationMillis: Long = 3_000L) { transientMessage = text; val generation = ++messageGeneration; updateStatus(); postDelayed({ if (generation == messageGeneration) { transientMessage = null; updateStatus() } }, durationMillis) }
    fun showFocus(x: Float, y: Float) { focusPoint = x to y; focusUntil = SystemClock.uptimeMillis() + 550; guide.invalidate() }
    fun showCaptureFlash() { flashUntil = SystemClock.uptimeMillis() + 100; guide.invalidate() }
    private fun updateStatus() {
        val text = transientMessage ?: when { saving -> "正在保存照片…"; searching -> "正在寻找有表现力的主体…"; ready -> "构图就绪，按快门拍摄" + cropShapeLabel(); cropping -> "正在收紧画面…"; compositionActive && subject == null -> "暂时跟丢，请保持取景或重新寻找"; compositionActive -> "沿箭头轻移手机" + (reason?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""); else -> "" }
        status.visibility = if (text.isEmpty()) GONE else VISIBLE; if (status.text.toString() != text) status.text = text
    }
    private fun cropShapeLabel():String {
        if(previewBounds.height()<=0 || retainedPhotoWidth==1f && retainedPhotoHeight==1f)return ""
        val ratio=previewBounds.width()*retainedPhotoWidth/(previewBounds.height()*retainedPhotoHeight)
        val shape=if(ratio>1.05f)"横幅" else if(ratio<.95f)"竖幅" else "近方形"
        return "\n$shape · "+String.format(Locale.US,"%.2f:1",ratio)
    }
    private fun showQualityMenu() { PopupMenu(context, qualityButton).apply { ResolutionPreset.entries.forEach { menu.add(it.label).setOnMenuItemClickListener { _ -> listener?.onResolutionSelected(it); true } }; show() } }
    override fun onDetachedFromWindow() { filterStrip.release(); super.onDetachedFromWindow() }
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
            if (gridVisible) {
                val grid = RectF(bounds.left, topBar.bottom + dp(8f), bounds.right, height - bottomInset - dp(24f))
                paint.color = 0x55FFFFFF; paint.strokeWidth = dp(.6f)
                for (fraction in listOf(.382f, .618f)) {
                    val x = grid.left + grid.width() * fraction; val y = grid.top + grid.height() * fraction
                    canvas.drawLine(x, grid.top, x, grid.bottom, paint); canvas.drawLine(grid.left, y, grid.right, y, paint)
                }
                val corner = dp(20f); paint.strokeWidth = dp(1f)
                for (x in listOf(grid.left, grid.right)) for (y in listOf(grid.top, grid.bottom)) {
                    canvas.drawLine(x, y, x + if (x == grid.left) corner else -corner, y, paint)
                    canvas.drawLine(x, y, x, y + if (y == grid.top) corner else -corner, paint)
                }
                if (!compositionActive) {
                    paint.color = theme.accent; paint.strokeWidth = dp(1f)
                    canvas.drawLine(bounds.centerX() - dp(11f), bounds.centerY(), bounds.centerX() + dp(11f), bounds.centerY(), paint)
                    canvas.drawLine(bounds.centerX(), bounds.centerY() - dp(11f), bounds.centerX(), bounds.centerY() + dp(11f), paint)
                }
            }
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
                val color = if (ready) theme.accent else theme.guide
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
            paint.strokeWidth = if (kind == "shutter") .65f else 1.7f
            when (kind) {
                "settings" -> { canvas.drawCircle(12f, 12f, 6.5f, paint); canvas.drawCircle(12f, 12f, 2.3f, paint); for (i in 0..7) { val a = i * PI / 4; canvas.drawLine(12f + cos(a).toFloat() * 7, 12f + sin(a).toFloat() * 7, 12f + cos(a).toFloat() * 9, 12f + sin(a).toFloat() * 9, paint) } }
                "filter" -> { canvas.drawCircle(9f, 9f, 5f, paint); canvas.drawCircle(15f, 9f, 5f, paint); canvas.drawCircle(12f, 15f, 5f, paint) }
                "compose" -> {
                    for ((x,y) in listOf(3f to 3f, 21f to 3f, 3f to 21f, 21f to 21f)) {
                        canvas.drawLine(x, y, x + if (x == 3f) 5f else -5f, y, paint)
                        canvas.drawLine(x, y, x, y + if (y == 3f) 5f else -5f, paint)
                    }
                    canvas.drawLine(8f, 12f, 16f, 12f, paint); canvas.drawLine(12f, 8f, 12f, 16f, paint)
                }
                "down", "up" -> { val y = if (kind == "down") 9f else 15f; val middle = if (kind == "down") 15f else 9f; val path = Path(); path.moveTo(6f,y); path.lineTo(12f,middle); path.lineTo(18f,y); canvas.drawPath(path,paint) }
                "flash", "flash-off" -> { val path = Path(); path.moveTo(13f,2f); path.lineTo(5f,13f); path.lineTo(11f,13f); path.lineTo(10f,22f); path.lineTo(19f,10f); path.lineTo(13f,10f); path.close(); canvas.drawPath(path,paint); if (kind == "flash-off") canvas.drawLine(3f,3f,21f,21f,paint) }
                "gallery" -> { canvas.drawRoundRect(3f,3f,21f,21f,2f,2f,paint); canvas.drawCircle(8f,8f,1.5f,paint); val path = Path(); path.moveTo(4f,18f); path.lineTo(10f,12f); path.lineTo(14f,16f); path.lineTo(17f,13f); path.lineTo(20f,16f); canvas.drawPath(path,paint) }
                "shutter" -> { canvas.drawCircle(12f, 12f, 10f, paint); paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 12f, 8.8f, paint); paint.style = Paint.Style.STROKE }
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
