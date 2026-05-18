package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.ml.MLRuntime
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject

/**
 * 主体检测步骤（order=200）。
 * 调用 MLRuntime 执行目标检测，将检测结果转为 DetectedSubject
 * 写入 context.subjects，供下游构图分析器使用。
 */
class SubjectDetectorStep @Inject constructor(
    private val mlRuntime: MLRuntime
) : AnalysisStep {

    override val id: String = "subject_detector"
    override val order: Int = 200

    /** ML 引擎不可用时自动禁用此步骤，Pipeline 会跳过 */
    override fun isEnabled(): Boolean = mlRuntime.isAvailable()

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val result = mlRuntime.detectObjects(frame.bitmap)

        val allSubjects = result.detections.map { detection ->
            val box = detection.boundingBox
            DetectedSubject(
                centerX = (box.left + box.right) / 2f,
                centerY = (box.top + box.bottom) / 2f,
                width = box.right - box.left,
                height = box.bottom - box.top,
                label = detection.label,
                confidence = detection.confidence
            )
        }

        // 按拍照模式过滤检测结果
        val filtered = filterByMode(allSubjects, context.cameraMode)

        return context.copy(subjects = filtered)
    }

    /** 根据拍照模式过滤主体列表 */
    private fun filterByMode(subjects: List<DetectedSubject>, mode: CameraMode): List<DetectedSubject> {
        return when (mode) {
            CameraMode.AUTO -> subjects
            CameraMode.LANDSCAPE -> emptyList()
            CameraMode.MACRO -> {
                // 微距模式：保留面积最大的单个目标
                val largest = subjects.maxByOrNull { it.width * it.height }
                listOfNotNull(largest)
            }
            else -> {
                // PORTRAIT / FOOD：按 allowedLabels 过滤
                if (mode.allowedLabels.isEmpty()) subjects
                else subjects.filter { it.label in mode.allowedLabels }
            }
        }
    }
}
