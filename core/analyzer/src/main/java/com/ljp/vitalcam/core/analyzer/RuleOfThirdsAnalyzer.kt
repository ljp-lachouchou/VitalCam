package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject

/**
 * 三分法构图分析器。
 * 评估主体位置是否接近三分线交叉点，并给出调整建议。
 * MVP 阶段在没有主体检测时返回默认引导。
 */
class RuleOfThirdsAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "rule_of_thirds"
    override val order: Int = 400

    override fun isEnabled(): Boolean = true

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        // 三分线交叉点坐标（归一化）
        val thirdPoints = listOf(
            0.33f to 0.33f,
            0.66f to 0.33f,
            0.33f to 0.66f,
            0.66f to 0.66f
        )

        if (context.subjects.isEmpty()) {
            // 没有检测到主体时，返回默认提示
            return context.copy(
                compositionScores = context.compositionScores + (id to CompositionScore(
                    ruleId = id,
                    score = 50,
                    description = "等待主体检测"
                )),
                guidances = context.guidances + Guidance(
                    message = "将主体对准网格交叉点",
                    direction = AdjustDirection.NONE,
                    priority = 100
                )
            )
        }

        // 计算主体到最近交叉点的距离，距离越小得分越高
        val subject = context.subjects.first()
        val minDistance = thirdPoints.minOf { (px, py) ->
            val dx = subject.centerX - px
            val dy = subject.centerY - py
            Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        // 归一化距离到得分：距离 0 → 100 分，距离 0.5 → 0 分
        val score = ((1f - (minDistance / 0.5f).coerceIn(0f, 1f)) * 100).toInt()

        // 根据主体位置生成方向建议
        val guidance = generateGuidance(subject.centerX, subject.centerY, thirdPoints)

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id,
                score = score,
                description = "三分法构图评分"
            )),
            guidances = context.guidances + guidance
        )
    }

    /** 根据主体位置与最近三分点的关系生成引导建议 */
    private fun generateGuidance(
        subjectX: Float,
        subjectY: Float,
        thirdPoints: List<Pair<Float, Float>>
    ): Guidance {
        val nearest = thirdPoints.minByOrNull { (px, py) ->
            val dx = subjectX - px
            val dy = subjectY - py
            dx * dx + dy * dy
        } ?: return Guidance("构图良好", AdjustDirection.NONE, 100)

        val dx = nearest.first - subjectX
        val dy = nearest.second - subjectY
        val threshold = 0.05f

        // 距离足够近，构图良好
        if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) {
            return Guidance("构图很棒！", AdjustDirection.NONE, 100)
        }

        // 选择偏移最大的方向给出建议
        return if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) {
                Guidance("向右移动一点", AdjustDirection.MOVE_RIGHT, 10)
            } else {
                Guidance("向左移动一点", AdjustDirection.MOVE_LEFT, 10)
            }
        } else {
            if (dy > 0) {
                Guidance("向下移动一点", AdjustDirection.MOVE_DOWN, 10)
            } else {
                Guidance("向上移动一点", AdjustDirection.MOVE_UP, 10)
            }
        }
    }
}
