package com.luyuan.silkzoom

import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Surface
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Debug-only state renderer for native screenshot checks; never performs an AI request. */
class UiPreviewActivity : ComponentActivity(), CameraOverlayView.Listener {
    private lateinit var cameraUi: CameraOverlayView
    private lateinit var host: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var zoomController: SmoothZoomController
    private val lutProcessor by lazy { LutSurfaceProcessor { } }
    private var debugFilter=StreetFilter.ORIGINAL
    private var debugStrength=1f
    private var displayZoom = 1f
    private var demoPlan: CompositionPlan? = null
    private var demoGoal = 3.2f
    private var capture: ImageCapture? = null
    private val photoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var retainedWidth = 1f
    private var retainedHeight = 1f
    private var provider: ProcessCameraProvider? = null
    private var promoGeneration = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT; window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        host = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); addView(preview, FrameLayout.LayoutParams(-1,-1)) }
        cameraUi = CameraOverlayView(this).apply { listener = this@UiPreviewActivity; setControlsEnabled(true); setZoom(1f); setFlashLabel("关"); setResolutionPreset(ResolutionPreset.PHOTO_12_MP) }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(host, FrameLayout.LayoutParams(-1,-1)); addView(cameraUi, FrameLayout.LayoutParams(-1,-1))
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets -> val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()); cameraUi.setSafeInsets(safe.top,safe.bottom,safe.left,safe.right); insets }
        })
        zoomController = SmoothZoomController { value ->
            displayZoom = value; preview.scaleX = value; preview.scaleY = value; cameraUi.setZoom(value)
            demoPlan?.let { plan ->
                val remaining = (demoGoal / value).coerceAtLeast(1f)
                val complete = kotlin.math.abs(demoGoal - value) < .00001f
                val target = plan.targetBeforeCrop(remaining)
                cameraUi.setZoomAnimating(!complete)
                cameraUi.setComposition(true, target, target, ready = complete, cropping = !complete,
                    reason = "演示：光影关系", cropFrame = if (complete) null else plan.cropFrame(target.first, target.second, remaining), goalZoom = demoGoal)
            }
        }.also { it.updateCapabilities(1f, 8f, 1f) }
        val mode = intent.getStringExtra("state") ?: "search"
        when (mode) {
            "crop-wide", "crop-ended" -> { retainedWidth = 1f; retainedHeight = .3f }
            "crop-tall" -> { retainedWidth = .55f; retainedHeight = 1f }
        }
        when (mode) {
            "smooth" -> cameraUi.setComposition(false)
            "search" -> cameraUi.setComposition(true, .45f to .45f, analyzing = true)
            "guide" -> cameraUi.setComposition(true, .58f to .45f, .34f to .34f, reason = "保留道路引导线", goalZoom = 3f)
            "crop-moving" -> {
                val plan = CompositionPlan(targetX = .34f, targetY = .6f, cropScale = 3.2f, cropWidth = 1f, cropHeight = .6f)
                cameraUi.setComposition(true, .62f to .45f, plan.targetBeforeCrop(3.2f),
                    reason = "保留道路引导线", cropFrame = plan.cropFrame(.62f, .45f, 3.2f), goalZoom = 3.2f)
            }
            "crop-wide" -> { cameraUi.setPhotoCrop(1f, .3f); cameraUi.setComposition(true, .5f to .5f, .5f to .5f, ready = true) }
            "crop-tall" -> { cameraUi.setPhotoCrop(.55f, 1f); cameraUi.setComposition(true, .5f to .5f, .5f to .5f, ready = true) }
            "crop-ended" -> { cameraUi.setPhotoCrop(1f, .3f); cameraUi.setComposition(false) }
            "ready" -> cameraUi.setComposition(true, .34f to .34f, .34f to .34f, ready = true)
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ provider = future.get(); bind() }, mainExecutor)
    }
    private fun bind() {
        if (preview.width < 20) { preview.post { bind() }; return }
        val useCase = Preview.Builder().setTargetRotation(preview.display?.rotation ?: Surface.ROTATION_0).build().also { it.surfaceProvider = preview.surfaceProvider }
        val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(preview.display?.rotation ?: Surface.ROTATION_0).build()
        capture = photo
        val group = UseCaseGroup.Builder().addUseCase(useCase).addUseCase(photo)
        preview.viewPort?.let { group.setViewPort(it) }
        group.addEffect(lutProcessor.effect)
        provider?.unbindAll(); provider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
    }
    override fun onPreviewAreaChanged(left: Int, top: Int, width: Int, height: Int) {
        host.layoutParams = FrameLayout.LayoutParams(width,height).apply { leftMargin=left;topMargin=top }
        if(intent.getStringExtra("state")=="crop-square") {
            val aspect=width.toFloat()/height
            retainedWidth=minOf(1f,1f/aspect);retainedHeight=minOf(1f,aspect)
            cameraUi.setPhotoCrop(retainedWidth,retainedHeight)
            cameraUi.setComposition(true,.5f to .5f,.5f to .5f,ready=true)
        }
    }
    override fun onCapture() {
        val photo = capture ?: return
        val width = retainedWidth; val height = retainedHeight; val zoom = displayZoom
        cameraUi.setSaving(true)
        photo.takePicture(photoExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val saved = runCatching { PhotoSaver.save(applicationContext, image, zoom, debugFilter, debugStrength, width, height) }
                runOnUiThread { cameraUi.setSaving(false); cameraUi.showMessage(if (saved.isSuccess) "已保存到相册" else "照片保存失败") }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { cameraUi.setSaving(false); cameraUi.showMessage("拍摄失败") }
            }
        })
    }
    override fun onAutoCompose() {
        if (intent.getBooleanExtra("promoFlow", false)) {
            val generation = ++promoGeneration
            cameraUi.setComposition(true, analyzing = true)
            cameraUi.postDelayed({
                if (isDestroyed || generation != promoGeneration) return@postDelayed
                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1400L
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { animation ->
                        if (isDestroyed || generation != promoGeneration) { cancel(); return@addUpdateListener }
                        val progress = animation.animatedValue as Float
                        cameraUi.setComposition(true, (.67f - .17f * progress) to (.43f + .07f * progress),
                            .5f to .5f, reason = "演示：保留空间与线条", goalZoom = demoGoal)
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (!isDestroyed && generation == promoGeneration) startDemoZoom(.31f)
                        }
                    })
                    start()
                }
            }, 1100L)
        } else startDemoZoom(.6f)
    }
    private fun startDemoZoom(height: Float) {
        demoPlan = CompositionPlan(targetX = .5f, targetY = .5f, cropScale = demoGoal, cropWidth = 1f, cropHeight = height)
        retainedWidth = 1f; retainedHeight = height
        cameraUi.setPhotoCrop(retainedWidth, retainedHeight)
        cameraUi.setComposition(true, .5f to .5f, .5f to .5f, cropping = true, reason = "演示：光影关系", goalZoom = demoGoal)
        cameraUi.setZoomAnimating(true)
        zoomController.frameTo(demoGoal)
    }
    override fun onEndComposition() { promoGeneration++; demoPlan = null; zoomController.beginInteraction(); cameraUi.setZoomAnimating(false); cameraUi.setComposition(false) }
    override fun onSettings() = Unit
    override fun onFilters() {
        if(cameraUi.filtersVisible)cameraUi.closePanels() else {
            cameraUi.openFilters(null);lutProcessor.snapshotRaw { bitmap -> if(bitmap!=null)runOnUiThread { if(!isDestroyed)cameraUi.setFilterPreview(bitmap) else bitmap.recycle() } }
        }
    }
    override fun onFilterChanged(filter:StreetFilter,strength:Float) { debugFilter=filter;debugStrength=strength;lutProcessor.setFilter(filter,strength);cameraUi.setFilter(filter,strength) }
    override fun onFilterInfo(filter:StreetFilter) { cameraUi.showMessage(filter.description) }
    override fun onOpenPhoto() { cameraUi.showMessage("调试预览，请在相机主界面查看照片") }
    override fun onCycleFlash() = Unit
    override fun onZoomGestureStart() { onEndComposition(); zoomController.beginInteraction() }
    override fun onZoomGestureEnd() { zoomController.endInteraction() }
    override fun onZoomTarget(zoomRatio: Float) { zoomController.followTo(zoomRatio) }
    override fun onLensStopSelected(zoomRatio: Float) { onEndComposition(); zoomController.animateTo(zoomRatio) }
    override fun onResolutionSelected(preset: ResolutionPreset) = Unit
    override fun onTapToFocus(x: Float, y: Float) = Unit
    override fun onDestroy() { if (::zoomController.isInitialized) zoomController.cancel(); provider?.unbindAll(); photoExecutor.shutdown();lutProcessor.close(); super.onDestroy() }
}
