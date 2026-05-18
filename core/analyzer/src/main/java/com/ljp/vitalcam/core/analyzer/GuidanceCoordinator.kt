package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject

/**
 * 引导协调器（order=900）。
 * 管道最后一步，实现分阶段引导：按维度层级（光线→色彩→构图→取景→姿势）
 * 找到第一个不达标的维度，只保留该维度的引导，避免多维度建议同时出现让用户困惑。
 * 所有评分仍保留（用于 overallScore），仅过滤引导显示。
 */
class GuidanceCoordinator @Inject constructor() : AnalysisStep {

    override val id: String = "guidance_coordinator"
    override val order: Int = 900
    override fun isEnabled(): Boolean = true

    companion object {
        private const val THRESHOLD = 60

        // 维度层级：从最基础到最精细
        private val HIERARCHY = listOf(
            "lighting",
            "color",
            "composition",
            "subject_framing",
            "pose_guidance"
        )
    }

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val scores = context.compositionScores

        // 找到第一个不达标的维度（未产出评分的维度视为达标，跳过）
        val blockingDimension = HIERARCHY.firstOrNull { dim ->
            val score = scores[dim]
            score != null && score.score < THRESHOLD
        }

        val filteredGuidances = if (blockingDimension != null) {
            // 只保留该维度产出的引导
            context.guidances.filter { it.sourceId == blockingDimension }
        } else {
            // 所有维度达标，保留全部引导（由 toResult() 按 priority 排序）
            context.guidances
        }

        return context.copy(guidances = filteredGuidances)
    }
}
