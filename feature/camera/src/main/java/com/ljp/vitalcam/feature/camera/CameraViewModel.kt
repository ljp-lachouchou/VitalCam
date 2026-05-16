package com.ljp.vitalcam.feature.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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
import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.pipeline.AnalysisPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

/** 相机 ViewModel，管理 CameraX 生命周期、分析管道调用和拍照功能 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val analysisPipeline: AnalysisPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _analysisResult = MutableStateFlow(AnalysisResult.EMPTY)
    val analysisResult: StateFlow<AnalysisResult> = _analysisResult.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /** 帧计数器，用于跳帧节流 */
    private var frameCount = 0

    /** 每 N 帧分析 1 帧 */
    private val analyzeEveryN = 3

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

                // 帧分析
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyzeFrame) }

                // 解绑旧用例再绑定新的
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )

                _uiState.value = CameraUiState.PreviewActive
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(e.message ?: "相机初始化失败")
            }
        }
    }

    /** ImageAnalysis 回调：节流 + 异步执行管道分析 */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        frameCount++
        // 跳帧：每 analyzeEveryN 帧才执行一次分析
        if (frameCount % analyzeEveryN != 0) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        val frameData = FrameData(
            bitmap = bitmap,
            width = imageProxy.width,
            height = imageProxy.height,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
        )
        imageProxy.close()

        viewModelScope.launch(Dispatchers.Default) {
            val result = analysisPipeline.execute(frameData)
            _analysisResult.value = result
        }
    }

    /** 拍照并保存到相册 */
    fun capturePhoto(context: Context) {
        val capture = imageCapture ?: return

        _uiState.value = CameraUiState.Capturing

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VitalCam_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VitalCam")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _uiState.value = CameraUiState.PreviewActive
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = CameraUiState.Error(exception.message ?: "拍照失败")
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
    }
}
