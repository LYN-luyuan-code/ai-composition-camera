package com.luyuan.silkzoom

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), CameraOverlayView.Listener {
    private lateinit var previewView: PreviewView
    private lateinit var previewHost: FrameLayout
    private lateinit var overlayView: CameraOverlayView
    private lateinit var aiSettings: AiSettingsStore
    private lateinit var lutStore: LutStore
    private lateinit var lutProcessor: LutSurfaceProcessor
    private val lutExecutor=Executors.newSingleThreadExecutor()
    private var gpuAvailable=true
    private var filterPreviewGeneration=0
    private var filterCategory="全部"
    private val importLut=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)lutExecutor.execute {
            val outcome=runCatching { lutStore.importFile(uri) }
            runOnUiThread {
                if(isDestroyed)return@runOnUiThread
                outcome.fold(onSuccess={ filter -> overlayView.setFilterCatalog(lutStore.all());setFilterCategory("我的LUT");onFilterChanged(filter,1f);if(!overlayView.filtersVisible)onFilters();overlayView.showMessage("已导入 ${filter.label}") },onFailure={ overlayView.showMessage(it.message ?: "LUT 导入失败",5000) })
            }
        }
    }
    private var settingsDialog: AlertDialog? = null
    private val appUpdates by lazy { AppUpdateController(this) }
    private var lastPhotoUri: android.net.Uri? = null
    private var thumbnailBitmap: Bitmap? = null
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private var aiRequest: AiCompositionClient? = null
    private var compositionAnalyzing = false
    private var compositionPlan = CompositionPlan()
    private var selectedSubject = .5f to .5f
    private var latestCompositionMatch: CompositionTracker.Match? = null
    private var latestMatchZoom = 1f
    private var compositionMissingSince = 0L
    private var compositionStarting = false
    private val compositionFrames = CompositionFrames()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var analysisAvailable = true
    private var previewAnalysis: ImageAnalysis? = null
    private val compositionHandler = Handler(Looper.getMainLooper())
    private val compositionExecutor = Executors.newSingleThreadExecutor()
    private var compositionTracker: CompositionTracker? = null
    private var searchTracker: CompositionTracker? = null
    private var searchReference: IntArray? = null
    private var searchMappingReliable = true
    private var compositionGeneration = 0
    private var compositionBusy = false
    private var compositionStableSince = 0L
    private var compositionCropZoom: Float? = null
    private var compositionCropStarted = 0L
    private var compositionCropped = false
    private var compositionScale = 1f
    private var compositionZoom: CompositionZoom? = null
    private var compositionZoomStepped = false
    private val aiTimeout = Runnable { if (compositionAnalyzing) finishAiFailure("AI 分析超时") }
    private val compositionTick = object : Runnable {
        override fun run() {
            val tracker = compositionTracker ?: searchTracker ?: return
            val searchingScene = compositionTracker == null
            if (!compositionBusy) {
                val frame = compositionFrame()
                if (frame != null) {
                    compositionBusy = true
                    val generation = compositionGeneration
                    val actual = actualCompositionZoom()
                    val scale = actual / (compositionZoom?.baseZoom ?: actual)
                    compositionExecutor.execute {
                        val match = runCatching { tracker.locate(frame, scale) }.getOrNull()
                        runOnUiThread {
                            if (generation == compositionGeneration && (compositionTracker != null || searchTracker != null)) {
                                compositionBusy = false
                                if (searchingScene) searchMappingReliable = match != null
                                else updateComposition(match, actual)
                            }
                        }
                    }
                } else { compositionStableSince = 0L; latestCompositionMatch = null; searchMappingReliable = false; pauseCompositionZoom(); refreshCompositionOverlay() }
            }
            compositionHandler.postDelayed(this, 75L)
        }
    }
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var zoomController: SmoothZoomController? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var photoResolution = ResolutionPreset.PHOTO_12_MP
    private var cameraStarting = false
    private var savingPhoto = false
    private var currentDisplayZoom = 1f
    private var retainedPhotoWidth = 1f
    private var retainedPhotoHeight = 1f
    private val globalMinZoom = 1f
    private val globalMaxZoom = 8f
    private var streetFilter = StreetFilter.ORIGINAL
    private var filterStrength = 1f
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else overlayView.showMessage("需要相机权限，请在系统设置中允许", 8_000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        previewHost = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            addView(previewView, FrameLayout.LayoutParams(-1, -1))
        }
        overlayView = CameraOverlayView(this).also { it.listener = this }
        aiSettings = AiSettingsStore(this)
        lutStore=LutStore(this)
        filterCategory=getSharedPreferences("camera_settings",MODE_PRIVATE).getString("filter_category","全部") ?: "全部"
        overlayView.setFilterCategory(filterCategory)
        lutProcessor=LutSurfaceProcessor { error -> mainExecutor.execute {
            if(!isDestroyed && gpuAvailable) {
                Log.e("StreetCamera","LUT preview failed",error);gpuAvailable=false
                streetFilter=StreetFilter.ORIGINAL;filterStrength=1f;applyFilter()
                overlayView.showMessage("设备滤镜预览不可用，已恢复原片",5000)
                if(cameraProvider!=null)bindCamera()
            }
        } }
        overlayView.setFilterCatalog(lutStore.all())
        val preferences = getSharedPreferences("camera_settings", MODE_PRIVATE)
        photoResolution = runCatching { ResolutionPreset.valueOf(preferences.getString("photo_resolution", "PHOTO_12_MP")!!) }.getOrDefault(ResolutionPreset.PHOTO_12_MP)
        streetFilter = lutStore.find(preferences.getString("street_filter", "ORIGINAL") ?: "ORIGINAL")
        filterStrength = preferences.getFloat("filter_strength", 1f).coerceIn(0f, 1f)
        overlayView.setResolutionPreset(photoResolution)
        overlayView.setZoomRange(globalMinZoom, globalMaxZoom)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(previewHost, FrameLayout.LayoutParams(-1, -1))
            addView(overlayView, FrameLayout.LayoutParams(-1, -1))
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                overlayView.setSafeInsets(safe.top, safe.bottom, safe.left, safe.right)
                insets
            }
        }
        setContentView(root)
        applyFilter()
        preferences.getString("last_photo_uri", null)?.takeIf { it.startsWith("content://media/") }?.let {
            lastPhotoUri = android.net.Uri.parse(it); refreshThumbnail(lastPhotoUri!!)
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!overlayView.closePanels()) { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }

    override fun onPreviewAreaChanged(left: Int, top: Int, width: Int, height: Int) {
        previewHost.layoutParams = FrameLayout.LayoutParams(width, height).apply { leftMargin = left; topMargin = top }
        if (camera != null && !cameraStarting) { cameraStarting = true; cancelComposition(); previewView.post { bindCamera() } }
    }
    override fun onStop() { cancelComposition(); super.onStop() }
    override fun onDestroy() {
        appUpdates.close()
        settingsDialog?.dismiss(); previewAnalysis?.clearAnalyzer(); zoomController?.cancel(); cameraProvider?.unbindAll()
        compositionExecutor.shutdownNow(); aiExecutor.shutdownNow(); analysisExecutor.shutdownNow()
        // Let a photograph already being written finish and clear its MediaStore pending flag.
        photoExecutor.shutdown();lutExecutor.shutdownNow();lutProcessor.close()
        super.onDestroy()
    }
    private fun startCamera() {
        if (cameraStarting) return
        cameraStarting = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try { cameraProvider = future.get(); bindCamera() }
            catch (error: Exception) { cameraStarting = false; overlayView.showMessage("相机启动失败，请重新打开应用", 6_000L) }
        }, mainExecutor)
    }
    private fun bindCamera() {
        val provider = cameraProvider ?: return
        if (previewView.width < 20 || previewView.height < 20) { previewView.post { bindCamera() }; return }
        cancelComposition(); compositionFrames.clear(); previewAnalysis?.clearAnalyzer(); zoomController?.cancel(); provider.unbindAll()
        setPhotoCrop(1f, 1f)
        overlayView.clearSelectedSubject(); selectedSubject = .5f to .5f; overlayView.setControlsEnabled(false)
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = previewView.surfaceProvider }
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(photoResolution.width, photoResolution.height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode).setResolutionSelector(resolution).setTargetRotation(rotation).build()
        imageCapture = capture
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture)
        if(gpuAvailable)group.addEffect(lutProcessor.effect)
        previewView.viewPort?.let { group.setViewPort(it) }
        if (analysisAvailable) {
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                .setTargetRotation(rotation).build()
            analysis.setAnalyzer(analysisExecutor) { image -> try { compositionFrames.offer(image) } finally { image.close() } }
            previewAnalysis = analysis; group.addUseCase(analysis)
        }
        try {
            camera = provider.bindToLifecycle(this, selector, group.build())
            // Only hardware zoom request: native 1x. All user/AI zooming is software cropping.
            camera!!.cameraControl.setZoomRatio(1f)
            zoomController = SmoothZoomController { zoom ->
                currentDisplayZoom = zoom
                previewView.scaleX = zoom; previewView.scaleY = zoom
                overlayView.setZoom(zoom)
                if (compositionTracker != null) refreshCompositionOverlay()
            }.also { it.updateCapabilities(globalMinZoom, globalMaxZoom, currentDisplayZoom) }
            if (!camera!!.cameraInfo.hasFlashUnit()) { flashMode = ImageCapture.FLASH_MODE_OFF; capture.flashMode = flashMode }
            updateFlashLabel(); overlayView.setControlsEnabled(true); cameraStarting = false
        } catch (error: Exception) {
            if (analysisAvailable) { analysisAvailable = false; previewAnalysis?.clearAnalyzer(); previewAnalysis = null; bindCamera(); return }
            camera = null; imageCapture = null; cameraStarting = false
            Log.e("StreetCamera", "Rear camera bind failed", error)
            overlayView.showMessage("主摄启动失败，请检查相机权限或关闭其他相机应用", 8_000L)
        }
    }

    private fun compositionFrame(): IntArray? {
        if (analysisAvailable) {
            val sensorToView = previewView.sensorToViewTransform ?: return null
            val transformed = Matrix(sensorToView).apply { postScale(currentDisplayZoom, currentDisplayZoom, previewView.width / 2f, previewView.height / 2f) }
            return compositionFrames.pixels(transformed, previewView.width, previewView.height, 160, 240)
        }
        val original = previewView.bitmap ?: return null
        try {
            val cropped = PhotoSaver.cropPreview(original, currentDisplayZoom)
            val scaled = Bitmap.createScaledBitmap(cropped, 160, 240, true)
            try {
                val pixels = IntArray(160 * 240)
                scaled.getPixels(pixels, 0, 160, 0, 0, 160, 240)
                return pixels
            } finally {
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== original) cropped.recycle()
            }
        } finally { original.recycle() }
    }

    override fun onEndComposition() { cancelComposition() }

    override fun onSettings() {
        if (settingsDialog?.isShowing == true) return
        cancelComposition()
        overlayView.closePanels()
        settingsDialog=AlertDialog.Builder(this).setTitle("相机设置")
            .setItems(arrayOf("AI 构图设置", "滤镜分类：$filterCategory", "当前滤镜说明：${streetFilter.label}", "导入 .cube LUT", "管理导入的 LUT", "检查更新 · ${BuildConfig.VERSION_NAME}")) { _,which ->
                when(which) {
                    0 -> settingsDialog=AiSettingsDialog.show(this,aiSettings) { overlayView.showMessage("构图设置已保存") }
                    1 -> {
                        val groups=arrayOf("全部","色彩","胶片","单色","我的LUT")
                        settingsDialog=AlertDialog.Builder(this).setTitle("滤镜分类").setSingleChoiceItems(groups,groups.indexOf(filterCategory)) { dialog,index ->
                            setFilterCategory(groups[index]);dialog.dismiss();if(!overlayView.filtersVisible)onFilters()
                        }.setNegativeButton("取消",null).show()
                    }
                    2 -> onFilterInfo(streetFilter)
                    3 -> onImportLut()
                    4 -> showImportedLuts()
                    5 -> appUpdates.check()
                }
            }.setNegativeButton("关闭",null).show()
    }

    override fun onAutoCompose() {
        if (camera == null || savingPhoto) return
        cancelComposition()
        try { aiSettings.load().validated() }
        catch (error: IllegalArgumentException) {
            overlayView.showMessage("请先在设置里填写 AI 供应商和 Key", 4_000L)
            return
        }
        val frame = compositionFrame() ?: run {
            overlayView.showMessage("取景画面尚未就绪，请稍后再试")
            return
        }
        setPhotoCrop(1f, 1f)
        val generation = ++compositionGeneration
        overlayView.clearSelectedSubject()
        compositionStarting = true; compositionAnalyzing = true
        searchReference = frame
        searchMappingReliable = true
        searchTracker = CompositionTracker(160, 240, frame, wholeFrame = true)
        overlayView.setComposition(true, analyzing = true)
        compositionHandler.post(compositionTick)
        startAiComposition(generation)
    }

    private fun startAiComposition(generation: Int) {
        val config = aiSettings.load()
        val original = previewView.bitmap ?: run { finishAiFailure("取景图不可用，请重试"); return }
        val snapshot = PhotoSaver.cropPreview(original, currentDisplayZoom)
        if (snapshot !== original) original.recycle()
        val request = AiCompositionClient()
        aiRequest = request
        val maxScale = (globalMaxZoom / actualCompositionZoom()).coerceIn(1f, 8f)
        compositionHandler.postDelayed(aiTimeout, 20_000L)
        aiExecutor.execute {
            val outcome = runCatching {
                try {
                    val factor = minOf(1f, 512f / maxOf(snapshot.width, snapshot.height))
                    val image = Bitmap.createScaledBitmap(snapshot,
                        (snapshot.width * factor).toInt().coerceAtLeast(1),
                        (snapshot.height * factor).toInt().coerceAtLeast(1), true)
                    val output = ByteArrayOutputStream()
                    try { check(image.compress(Bitmap.CompressFormat.JPEG, 70, output)) }
                    finally { if (image !== snapshot) image.recycle() }
                    request.analyze(config, output.toByteArray(), null, maxScale, snapshot.width.toFloat()/snapshot.height)
                } finally { snapshot.recycle() }
            }
            runOnUiThread {
                if (generation != compositionGeneration) return@runOnUiThread
                aiRequest = null
                compositionHandler.removeCallbacks(aiTimeout)
                outcome.fold(onSuccess = { plan ->
                    activateDiscoveredSubject(generation, plan)
                }, onFailure = { failure ->
                    val message = when (failure) {
                        is AiCompositionException, is IllegalArgumentException -> failure.message ?: "AI 分析失败"
                        else -> "AI 连接失败，请检查网络和接口设置"
                    }
                    finishAiFailure(message)
                })
            }
        }
    }

    /** Move the AI's selected point from the submitted scene into the current live frame. */
    private fun activateDiscoveredSubject(generation: Int, plan: CompositionPlan) {
        val frame = compositionFrame() ?: run { finishAiFailure("当前取景尚未就绪，请重新寻找"); return }
        val sceneTracker = searchTracker
        val reference = searchReference
        val reliable = searchMappingReliable
        val sampledZoom = currentDisplayZoom
        searchTracker = null; searchReference = null
        compositionBusy = true; compositionStarting = true
        compositionHandler.removeCallbacks(compositionTick)
        compositionExecutor.execute {
            val result = runCatching {
                val point = when {
                    reliable && sceneTracker != null -> sceneTracker.mapOriginalPoint(plan.subjectX, plan.subjectY)
                    reference != null && SubjectPriority.sameScene(reference, frame) -> plan.subjectX to plan.subjectY
                    else -> throw IllegalArgumentException("取景变化较大，请保持画面后重新寻找")
                }
                require(point.first in .04f.. .96f && point.second in .04f.. .96f) { "发现的主体已离开画面，请重新寻找" }
                var tracker = CompositionTracker(160, 240, frame, point.first, point.second, featureRadiusFraction = .20f)
                var match = tracker.locate(frame)
                if (match == null) {
                    // Static artistic subjects may be smooth shadows or reflections: use surrounding scene features.
                    tracker = CompositionTracker(160, 240, frame, point.first, point.second, wholeFrame = true)
                    match = tracker.locate(frame)
                }
                requireNotNull(match) { "发现的主体暂时无法稳定跟踪，请保持画面后重试" }
                tracker to match
            }
            runOnUiThread {
                if (generation != compositionGeneration) return@runOnUiThread
                compositionBusy = false; compositionStarting = false; compositionAnalyzing = false
                result.fold(onSuccess = { (tracker, match) ->
                    compositionPlan = plan
                    compositionTracker = tracker
                    selectedSubject = match.x to match.y
                    latestCompositionMatch = match; latestMatchZoom = sampledZoom
                    compositionZoom = CompositionZoom(sampledZoom, plan.cropScale, globalMaxZoom)
                    compositionScale = compositionZoom!!.totalScale
                    compositionStableSince = 0L
                    refreshCompositionOverlay()
                    compositionHandler.post(compositionTick)
                }, onFailure = { finishAiFailure(it.message ?: "主体寻找失败，请重试") })
            }
        }
    }

    private fun finishAiFailure(message: String) {
        cancelComposition()
        overlayView.showMessage(message + " · 可重新构图", 5_000L)
    }

    private fun renderedMatch(): CompositionTracker.Match? = latestCompositionMatch?.let {
        val point = CompositionPlan.mapZoomPoint(it.x, it.y, latestMatchZoom, currentDisplayZoom)
        if (point.first in 0f..1f && point.second in 0f..1f) CompositionTracker.Match(point.first, point.second) else null
    }

    private fun refreshCompositionOverlay() {
        if (compositionCropped && !compositionAnalyzing) setPhotoCrop(compositionPlan.cropWidth, compositionPlan.cropHeight)
        val remaining = compositionZoom?.remaining(actualCompositionZoom()) ?: 1f
        val displayedMatch = renderedMatch()
        val frame = if (!compositionAnalyzing && !compositionCropped && (remaining > 1.04f ||
                compositionPlan.cropWidth < .999f || compositionPlan.cropHeight < .999f)) displayedMatch?.let {
            compositionPlan.cropFrame(it.x, it.y, remaining)
        } else null
        overlayView.setComposition(compositionTracker != null || compositionStarting || compositionAnalyzing,
            displayedMatch?.let { it.x to it.y }, compositionTarget(),
            ready = !compositionAnalyzing && compositionMissingSince == 0L && compositionCropped && displayedMatch != null && compositionIsAligned(displayedMatch, compositionTarget()),
            analyzing = compositionAnalyzing || compositionStarting, reason = listOf(compositionPlan.subjectLabel, compositionPlan.reason).filter { it.isNotBlank() }.joinToString(" · "),
            cropping = compositionCropZoom != null && compositionScale > 1.01f,
            cropApplied = compositionCropped && compositionScale > 1.01f,
            cropFrame = frame, goalZoom = compositionZoom?.goal)
    }

    private fun compositionIsAligned(match: CompositionTracker.Match, target: Pair<Float, Float>): Boolean {
        val dx = (match.x - target.first) * overlayView.previewBounds.width()
        val dy = (match.y - target.second) * overlayView.previewBounds.height()
        val radius = overlayView.guideRadius
        return dx * dx + dy * dy <= radius * radius
    }

    private fun setPhotoCrop(width: Float, height: Float) {
        retainedPhotoWidth = width; retainedPhotoHeight = height
        overlayView.setPhotoCrop(width, height)
    }

    private fun actualCompositionZoom(): Float = currentDisplayZoom

    private fun compositionTarget(): Pair<Float, Float> =
        compositionPlan.targetBeforeCrop(compositionZoom?.remaining(actualCompositionZoom()) ?: compositionScale)

    private fun updateComposition(sample: CompositionTracker.Match?, sampleZoom: Float = currentDisplayZoom) {
        val match = sample?.let {
            val p = CompositionPlan.mapZoomPoint(it.x, it.y, sampleZoom, currentDisplayZoom)
            if (p.first in 0f..1f && p.second in 0f..1f) CompositionTracker.Match(p.first, p.second) else null
        }
        val requestedZoom = compositionCropZoom
        if (requestedZoom != null) {
            val actual = currentDisplayZoom
            if (SystemClock.elapsedRealtime() - compositionCropStarted > 4_000L) {
                cancelComposition()
                overlayView.showMessage("自动裁切未完成，请重新构图")
                return
            }
            if (zoomController?.isAnimating == false && kotlin.math.abs(actual - requestedZoom) < .001f) {
                compositionCropZoom = null
                overlayView.setZoomAnimating(false)
                compositionCropped = compositionZoom?.finished(actual) == true
                compositionZoomStepped = true
                compositionStableSince = 0L
            }
        }
        val target = compositionTarget()
        if (match == null) {
            if (compositionMissingSince == 0L) compositionMissingSince = SystemClock.elapsedRealtime()
            if (SystemClock.elapsedRealtime() - compositionMissingSince > 250L) latestCompositionMatch = null
        } else {
            compositionMissingSince = 0L
            latestCompositionMatch = match
            latestMatchZoom = currentDisplayZoom
        }
        val aligned = match != null && compositionIsAligned(match, target)
        if (compositionCropZoom != null && !aligned) {
            zoomController?.beginInteraction()
            compositionCropZoom = null
            overlayView.setZoomAnimating(false)
            compositionStableSince = 0L
        }
        if (aligned && compositionCropZoom == null && !compositionAnalyzing) {
            val now = SystemClock.elapsedRealtime()
            if (compositionStableSince == 0L) compositionStableSince = now
            val dwell = if (compositionZoomStepped) 150L else 450L
            if (!compositionCropped && now - compositionStableSince >= dwell) {
                val zoom = compositionZoom ?: return
                val actual = actualCompositionZoom()
                if (zoom.finished(actual)) {
                    compositionCropped = true
                } else {
                    val crop = zoom.goal.coerceIn(globalMinZoom, globalMaxZoom)
                    compositionCropZoom = crop
                    compositionCropStarted = now
                    setPhotoCrop(compositionPlan.cropWidth, compositionPlan.cropHeight)
                    overlayView.setZoomAnimating(true)
                    zoomController?.frameTo(crop)
                }
            }
        } else {
            compositionStableSince = 0L
        }
        refreshCompositionOverlay()
    }

    private fun pauseCompositionZoom() {
        if (compositionCropZoom == null) return
        zoomController?.beginInteraction()
        compositionCropZoom = null
        overlayView.setZoomAnimating(false)
        compositionStableSince = 0L
    }

    private fun cancelComposition() {
        latestCompositionMatch?.let {
            selectedSubject = it.x.coerceIn(0.1f, 0.9f) to it.y.coerceIn(0.1f, 0.9f)
            overlayView.setSelectedSubject(selectedSubject)
        }
        compositionGeneration++
        if (compositionCropZoom != null) zoomController?.beginInteraction()
        aiRequest?.cancel()
        aiRequest = null
        compositionAnalyzing = false
        compositionStarting = false
        compositionBusy = false
        latestCompositionMatch = null
        compositionMissingSince = 0L
        compositionHandler.removeCallbacks(aiTimeout)
        compositionTracker = null
        searchTracker = null; searchReference = null
        compositionHandler.removeCallbacks(compositionTick)
        compositionStableSince = 0L
        compositionCropZoom = null
        compositionCropped = false
        compositionZoom = null
        compositionZoomStepped = false
        overlayView.setZoomAnimating(false)
        overlayView.setComposition(false)
    }

    override fun onCapture() {
        val capture = imageCapture ?: return
        if (savingPhoto || cameraStarting) return
        // The photographer can capture immediately, including during automatic framing.
        cancelComposition()
        zoomController?.beginInteraction()
        val zoom = currentDisplayZoom
        val retainedWidth = retainedPhotoWidth
        val retainedHeight = retainedPhotoHeight
        val displayed=if(gpuAvailable)lutProcessor.displayed else LutSurfaceProcessor.Choice(StreetFilter.ORIGINAL,1f)
        val filter = displayed.filter
        val strength = displayed.strength
        savingPhoto = true; overlayView.setSaving(true); overlayView.showCaptureFlash()
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        capture.takePicture(photoExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val outcome = runCatching { PhotoSaver.save(applicationContext, image, zoom, filter, strength, retainedWidth, retainedHeight) }
                runOnUiThread {
                    savingPhoto = false; overlayView.setSaving(false)
                    outcome.getOrNull()?.let { uri ->
                        lastPhotoUri = uri
                        getSharedPreferences("camera_settings", MODE_PRIVATE).edit().putString("last_photo_uri", uri.toString()).apply()
                        if (!isDestroyed) refreshThumbnail(uri)
                    }
                    overlayView.showMessage(if (outcome.isSuccess) "已保存到相册" else "照片保存失败，请重试")
                }
            }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { savingPhoto = false; overlayView.setSaving(false); overlayView.showMessage("拍摄失败，请重试") } }
        })
    }
    override fun onFilters() {
        if (overlayView.filtersVisible) { overlayView.closePanels(); return }
        cancelComposition(); zoomController?.beginInteraction()
        overlayView.openFilters(null)
        val generation=++filterPreviewGeneration;val zoom=currentDisplayZoom
        fun deliver(original:Bitmap?) {
            if(original==null)return
            val cropped=PhotoSaver.cropPreview(original,zoom)
            val factor=minOf(1f,256f/maxOf(cropped.width,cropped.height))
            val sample=Bitmap.createScaledBitmap(cropped,(cropped.width*factor).toInt().coerceAtLeast(1),(cropped.height*factor).toInt().coerceAtLeast(1),true)
            if(cropped!==original && cropped!==sample)cropped.recycle();if(original!==sample)original.recycle()
            runOnUiThread { if(isDestroyed||generation!=filterPreviewGeneration||!overlayView.filtersVisible)sample.recycle() else overlayView.setFilterPreview(sample) }
        }
        if(gpuAvailable)lutProcessor.snapshotRaw(::deliver) else deliver(previewView.bitmap)
    }
    private fun setFilterCategory(category:String) {
        filterCategory=category;overlayView.setFilterCategory(category)
        getSharedPreferences("camera_settings",MODE_PRIVATE).edit().putString("filter_category",category).apply()
    }
    private fun showImportedLuts() {
        val imported=lutStore.all().filter { it.imported }
        settingsDialog=if(imported.isEmpty()) AlertDialog.Builder(this).setTitle("我的 LUT")
            .setMessage("还没有导入 LUT。选择 .cube 文件后，它会保存在应用内，可随时调整强度或删除。")
            .setPositiveButton("导入 LUT") { _,_ -> onImportLut() }.setNegativeButton("关闭",null).show()
        else AlertDialog.Builder(this).setTitle("我的 LUT").setItems(imported.map { it.label }.toTypedArray()) { _,index -> onFilterInfo(imported[index]) }
            .setNegativeButton("关闭",null).show()
    }
    private fun onImportLut() { cancelComposition();importLut.launch(arrayOf("*/*")) }
    override fun onFilterInfo(filter:StreetFilter) {
        val builder=AlertDialog.Builder(this).setTitle(filter.label).setMessage(filter.description).setPositiveButton("关闭",null)
        if(filter.imported)builder.setNeutralButton("删除 LUT") { _,_ ->
            if(streetFilter.name==filter.name)onFilterChanged(StreetFilter.ORIGINAL,1f)
            lutStore.delete(filter);overlayView.setFilterCatalog(lutStore.all());overlayView.showMessage("已删除导入的 LUT")
        }
        settingsDialog=builder.show()
    }
    override fun onFilterChanged(filter: StreetFilter, strength: Float) {
        if(!gpuAvailable && filter!==StreetFilter.ORIGINAL){overlayView.showMessage("设备暂不支持 LUT 实时预览，请使用原片");return}
        streetFilter = filter; filterStrength = strength
        getSharedPreferences("camera_settings", MODE_PRIVATE).edit().putString("street_filter", filter.name).putFloat("filter_strength", strength).apply()
        applyFilter()
    }
    override fun onOpenPhoto() {
        val uri = lastPhotoUri ?: run { overlayView.showMessage("拍摄后可在这里查看最近照片"); return }
        try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: android.content.ActivityNotFoundException) { overlayView.showMessage("没有可用的图片查看器") }
    }
    private fun refreshThumbnail(uri: android.net.Uri) {
        photoExecutor.execute {
            val bitmap = runCatching { contentResolver.loadThumbnail(uri, Size(160, 160), null) }.getOrNull()
            runOnUiThread {
                if (isDestroyed || lastPhotoUri != uri) { bitmap?.recycle(); return@runOnUiThread }
                bitmap?.let { val old = thumbnailBitmap; thumbnailBitmap = it; overlayView.setPhotoThumbnail(it); old?.recycle() }
            }
        }
    }
    private fun applyFilter() {
        if(gpuAvailable)lutProcessor.setFilter(streetFilter,filterStrength)
        overlayView.setFilter(streetFilter, filterStrength)
    }
    override fun onCycleFlash() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) { overlayView.showMessage("当前主摄没有闪光灯"); return }
        flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
        imageCapture?.flashMode = flashMode; updateFlashLabel()
    }
    private fun updateFlashLabel() { overlayView.setFlashLabel(when (flashMode) { ImageCapture.FLASH_MODE_AUTO -> "自动"; ImageCapture.FLASH_MODE_ON -> "开"; else -> "关" }) }
    override fun onZoomGestureStart() { cancelComposition(); zoomController?.beginInteraction() }
    override fun onZoomGestureEnd() { zoomController?.endInteraction() }
    override fun onZoomTarget(zoomRatio: Float) { zoomController?.followTo(zoomRatio.coerceIn(globalMinZoom, globalMaxZoom)) }
    override fun onLensStopSelected(zoomRatio: Float) { cancelComposition(); requestDisplayZoom(zoomRatio) }
    private fun requestDisplayZoom(requestedZoom: Float) { zoomController?.animateTo(requestedZoom.coerceIn(globalMinZoom, globalMaxZoom)) }
    override fun onResolutionSelected(preset: ResolutionPreset) {
        if (photoResolution == preset || savingPhoto) return
        photoResolution = preset
        getSharedPreferences("camera_settings", MODE_PRIVATE).edit().putString("photo_resolution", preset.name).apply()
        overlayView.setResolutionPreset(preset); cameraStarting = true; bindCamera()
    }
    override fun onTapToFocus(x: Float, y: Float) {
        val bound = camera ?: return
        val rect = overlayView.previewBounds
        if (!rect.contains(x, y)) return
        cancelComposition()
        selectedSubject = ((x - rect.left) / rect.width()).coerceIn(.1f, .9f) to ((y - rect.top) / rect.height()).coerceIn(.1f, .9f)
        val nativeX = previewView.width / 2f + (x - rect.centerX()) / currentDisplayZoom
        val nativeY = previewView.height / 2f + (y - rect.centerY()) / currentDisplayZoom
        val point = previewView.meteringPointFactory.createPoint(nativeX, nativeY)
        bound.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
    }
}
