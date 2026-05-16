package com.ljp.vitalcam.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe ObjectDetector 实现。
 * 使用 efficientdet_lite0.tflite 检测 90 类 COCO 目标（人、车、动物等），
 * 以 IMAGE 模式逐帧推理并将像素坐标归一化为 0.0~1.0。
 */
@Singleton
class MediaPipeMLRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) : MLRuntime {

    override val name: String = "mediapipe-object-detector"

    companion object {
        private const val TAG = "MediaPipeMLRuntime"
        private const val MODEL_ASSET = "efficientdet_lite0.tflite"
        private const val MAX_RESULTS = 5
        private const val SCORE_THRESHOLD = 0.5f
    }

    /** 惰性初始化的检测器实例 */
    private var detector: ObjectDetector? = null

    /** 模型加载是否失败，失败后不再重试 */
    private var initFailed = false

    override fun isAvailable(): Boolean = !initFailed

    override suspend fun detectObjects(input: Bitmap): ObjectDetectionResult {
        val det = getOrCreateDetector() ?: return ObjectDetectionResult.EMPTY

        return try {
            val mpImage = BitmapImageBuilder(input).build()
            val result = det.detect(mpImage)

            val detections = result.detections().map { detection ->
                val bbox = detection.boundingBox()
                val category = detection.categories().firstOrNull()

                // MediaPipe 返回像素坐标，归一化到 0.0~1.0
                val normalizedBox = RectF(
                    (bbox.left / input.width).coerceIn(0f, 1f),
                    (bbox.top / input.height).coerceIn(0f, 1f),
                    (bbox.right / input.width).coerceIn(0f, 1f),
                    (bbox.bottom / input.height).coerceIn(0f, 1f)
                )

                Detection(
                    boundingBox = normalizedBox,
                    label = category?.categoryName() ?: "unknown",
                    confidence = category?.score() ?: 0f
                )
            }

            ObjectDetectionResult(detections = detections)
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            ObjectDetectionResult.EMPTY
        }
    }

    override fun close() {
        detector?.close()
        detector = null
    }

    /** 惰性创建 ObjectDetector 实例，失败后标记 initFailed 不再重试 */
    @Synchronized
    private fun getOrCreateDetector(): ObjectDetector? {
        if (initFailed) return null
        detector?.let { return it }

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()

            ObjectDetector.createFromOptions(context, options).also {
                detector = it
                Log.d(TAG, "ObjectDetector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector", e)
            initFailed = true
            null
        }
    }
}
