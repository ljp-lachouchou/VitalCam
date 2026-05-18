package com.ljp.vitalcam.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.ljp.vitalcam.core.common.PersonPose
import com.ljp.vitalcam.core.common.PoseLandmark
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe PoseLandmarker 实现。
 * 使用 pose_landmarker_lite.task 检测人体 33 个关键点，
 * 以 IMAGE 模式逐帧推理，坐标已为归一化值（0.0~1.0）。
 */
@Singleton
class MediaPipePoseRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) : PoseRuntime {

    override val name: String = "mediapipe-pose-landmarker"

    companion object {
        private const val TAG = "MediaPipePoseRuntime"
        private const val MODEL_ASSET = "pose_landmarker_lite.task"
        private const val NUM_POSES = 2
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
    }

    private var landmarker: PoseLandmarker? = null
    private var initFailed = false

    override fun isAvailable(): Boolean = !initFailed

    override suspend fun detectPose(input: Bitmap): PoseDetectionResult {
        val lm = getOrCreateLandmarker() ?: return PoseDetectionResult.EMPTY

        return try {
            val mpImage = BitmapImageBuilder(input).build()
            val result = lm.detect(mpImage)

            val poses = result.landmarks().map { landmarkList ->
                PersonPose(
                    landmarks = landmarkList.map { landmark ->
                        PoseLandmark(
                            x = landmark.x(),
                            y = landmark.y(),
                            z = landmark.z(),
                            visibility = landmark.visibility().orElse(0f)
                        )
                    }
                )
            }

            PoseDetectionResult(poses = poses)
        } catch (e: Exception) {
            Log.e(TAG, "Pose detection failed", e)
            PoseDetectionResult.EMPTY
        }
    }

    override fun close() {
        landmarker?.close()
        landmarker = null
    }

    @Synchronized
    private fun getOrCreateLandmarker(): PoseLandmarker? {
        if (initFailed) return null
        landmarker?.let { return it }

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(NUM_POSES)
                .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .build()

            PoseLandmarker.createFromOptions(context, options).also {
                landmarker = it
                Log.d(TAG, "PoseLandmarker initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PoseLandmarker", e)
            initFailed = true
            null
        }
    }
}
