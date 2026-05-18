package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.ml.PoseRuntime
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject

/**
 * 姿势检测步骤（order=250）。
 * 仅在人像模式下调用 PoseRuntime 检测人体关键点，
 * 将结果写入 context.poseLandmarks 供下游 PoseGuidanceAnalyzer 使用。
 */
class PoseLandmarkStep @Inject constructor(
    private val poseRuntime: PoseRuntime
) : AnalysisStep {

    override val id: String = "pose_landmark"
    override val order: Int = 250
    override fun isEnabled(): Boolean = poseRuntime.isAvailable()

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        // 仅人像模式执行姿势检测
        if (context.cameraMode != CameraMode.PORTRAIT) return context

        val result = poseRuntime.detectPose(frame.bitmap)
        return context.copy(poseLandmarks = result.poses)
    }
}
