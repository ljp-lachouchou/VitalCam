package com.ljp.vitalcam.core.pipeline

import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.common.PersonPose
import com.ljp.vitalcam.core.common.SceneType

/** 管道步骤间传递的共享上下文，通过 copy() 保证不可变性 */
data class AnalysisContext(
    /** 当前拍照模式 */
    val cameraMode: CameraMode = CameraMode.AUTO,
    /** 检测到的主体列表 */
    val subjects: List<DetectedSubject> = emptyList(),
    /** 检测到的人体姿势列表 */
    val poseLandmarks: List<PersonPose> = emptyList(),
    /** 场景分类 */
    val sceneType: SceneType? = null,
    /** 各构图规则的评分 */
    val compositionScores: Map<String, CompositionScore> = emptyMap(),
    /** 水平倾斜角度（度） */
    val horizontalTilt: Float = 0f,
    /** 引导建议列表 */
    val guidances: List<Guidance> = emptyList(),
    /** 扩展元数据，供自定义 Step 存取中间数据 */
    val metadata: Map<String, Any> = emptyMap()
) {
    /** 将上下文转换为最终分析结果 */
    fun toResult(): AnalysisResult {
        val overallScore = if (compositionScores.isEmpty()) {
            0
        } else {
            compositionScores.values.map { it.score }.average().toInt()
        }

        val sortedGuidances = guidances.sortedBy { it.priority }

        return AnalysisResult(
            overallScore = overallScore,
            dimensionScores = compositionScores,
            guidances = sortedGuidances,
            subjects = subjects,
            metadata = metadata
        )
    }
}
