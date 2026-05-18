package com.ljp.vitalcam.feature.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.pipeline.AnalysisPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.abs

/** 相机 ViewModel，管理 CameraX 生命周期、分析管道调用和拍照功能 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val analysisPipeline: AnalysisPipeline,
    private val orientationProvider: DeviceOrientationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _analysisResult = MutableStateFlow(AnalysisResult.EMPTY)
    val analysisResult: StateFlow<AnalysisResult> = _analysisResult.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.AUTO)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    /** 拍照时的分析结果快照，报告页读取 */
    private val _captureResult = MutableStateFlow<AnalysisResult?>(null)
    val captureResult: StateFlow<AnalysisResult?> = _captureResult.asStateFlow()

    /** 拍照保存后的照片 URI */
    private val _capturedPhotoUri = MutableStateFlow<Uri?>(null)
    val capturedPhotoUri: StateFlow<Uri?> = _capturedPhotoUri.asStateFlow()

    /** 拍照时的 roll 角度，供滤镜矫正用 */
    private val _captureRollDegrees = MutableStateFlow(0f)
    val captureRollDegrees: StateFlow<Float> = _captureRollDegrees.asStateFlow()

    /** 消费 CaptureSuccess 状态，重置为 PreviewActive */
    fun onCaptureResultConsumed() {
        _uiState.value = CameraUiState.PreviewActive
    }

    /** 切换拍照模式，同时重置 zoom */
    fun setCameraMode(mode: CameraMode) {
        _cameraMode.value = mode
        resetZoom()
    }

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** 分析忙碌标志：保证同时只有一个分析任务在执行 */
    private val isAnalyzing = AtomicBoolean(false)

    /** 帧分析节流：两次分析之间的最小间隔（ms），降低 CPU/GPU 发热 */
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 500L

    // ── 自动 Zoom 状态 ──
    private var currentZoomRatio = 1.0f
    private var lastZoomTime = 0L
    private var consecutiveZoomInCount = 0
    private var consecutiveZoomOutCount = 0

    /** 绑定 CameraX 到生命周期，启动预览和分析 */
    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()

                // 预览
                val preview = Preview.Builder().build().also { previewUseCase ->
                    previewUseCase.setSurfaceProvider { request ->
                        _surfaceRequest.value = request
                    }
                }

                // 拍照
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                // 帧分析：降低分辨率加速 ML 推理
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyzeFrame) }

                // 解绑旧用例再绑定新的
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )

                orientationProvider.start()
                _uiState.value = CameraUiState.PreviewActive
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(e.message ?: "相机初始化失败")
            }
        }
    }

    /** ImageAnalysis 回调：节流 + 忙碌检查，避免过度计算导致发热 */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        // 节流：距上次分析不足 500ms，直接丢弃
        if (now - lastAnalysisTime < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        // 上一帧还在分析中，直接丢弃当前帧
        if (!isAnalyzing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = now

        val frameData: FrameData
        try {
            val rawBitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            // 旋转到屏幕方向，使 ML 检测坐标与预览坐标系一致
            val bitmap = rotateBitmap(rawBitmap, rotation)
            frameData = FrameData(
                bitmap = bitmap,
                width = bitmap.width,
                height = bitmap.height,
                rotationDegrees = 0,
                timestampMs = imageProxy.imageInfo.timestamp / 1_000_000,
                rollDegrees = orientationProvider.rollDegrees,
                pitchDegrees = orientationProvider.pitchDegrees
            )
        } catch (_: Exception) {
            // toBitmap() 失败时必须重置标志，否则后续帧全部被丢弃
            isAnalyzing.set(false)
            imageProxy.close()
            return
        }
        imageProxy.close()

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = analysisPipeline.execute(frameData, _cameraMode.value)
                _analysisResult.value = result
                applyAutoZoom(result)
            } finally {
                isAnalyzing.set(false)
            }
        }
    }

    // ── 自动 Zoom ──

    /** 根据分析结果自动调整 zoom，需连续 3 帧同方向 + 1.5s 冷却 */
    private fun applyAutoZoom(result: AnalysisResult) {
        val cam = camera ?: return
        val now = System.currentTimeMillis()
        if (now - lastZoomTime < 1500L) return

        val topGuidance = result.guidances.firstOrNull() ?: return

        when (topGuidance.direction) {
            AdjustDirection.ZOOM_IN -> {
                consecutiveZoomOutCount = 0
                consecutiveZoomInCount++
                if (consecutiveZoomInCount >= 3) {
                    adjustZoom(cam, +0.2f)
                    consecutiveZoomInCount = 0
                }
            }
            AdjustDirection.ZOOM_OUT -> {
                consecutiveZoomInCount = 0
                consecutiveZoomOutCount++
                if (consecutiveZoomOutCount >= 3) {
                    adjustZoom(cam, -0.2f)
                    consecutiveZoomOutCount = 0
                }
            }
            else -> {
                consecutiveZoomInCount = 0
                consecutiveZoomOutCount = 0
            }
        }
    }

    private fun adjustZoom(cam: Camera, delta: Float) {
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val target = (currentZoomRatio + delta)
            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        if (abs(target - currentZoomRatio) < 0.05f) return

        cam.cameraControl.setZoomRatio(target)
        currentZoomRatio = target
        lastZoomTime = System.currentTimeMillis()
    }

    private fun resetZoom() {
        camera?.cameraControl?.setZoomRatio(1.0f)
        currentZoomRatio = 1.0f
        consecutiveZoomInCount = 0
        consecutiveZoomOutCount = 0
    }

    /** 拍照并保存到私有缓存目录，用户在编辑页点击"保存"后才写入相册 */
    fun capturePhoto(context: Context) {
        val capture = imageCapture ?: return

        _captureResult.value = _analysisResult.value
        _captureRollDegrees.value = orientationProvider.rollDegrees
        _uiState.value = CameraUiState.Capturing

        // 保存到 app 私有缓存，不直接入相册
        val tempFile = java.io.File(context.cacheDir, "vitalcam_temp_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _capturedPhotoUri.value = android.net.Uri.fromFile(tempFile)
                    _uiState.value = CameraUiState.CaptureSuccess
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = CameraUiState.Error(exception.message ?: "拍照失败")
                }
            }
        )
    }

    /** 编辑页返回时清理临时文件 */
    fun cleanupTempPhoto() {
        val uri = _capturedPhotoUri.value ?: return
        val path = uri.path ?: return
        val file = java.io.File(path)
        if (file.exists() && file.name.startsWith("vitalcam_temp_")) {
            file.delete()
        }
        _capturedPhotoUri.value = null
    }

    /** 按指定角度旋转 bitmap，0 度时直接返回原图 */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onCleared() {
        super.onCleared()
        orientationProvider.stop()
        analysisExecutor.shutdown()
    }
}
