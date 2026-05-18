package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 构图分析器，根据主体数量采用不同策略：
 * 1 个主体 → 三分法；2 个主体 → 对称/对角平衡；3+ 个主体 → 分布均匀度。
 */
class CompositionAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "composition"
    override val order: Int = 400
    override fun isEnabled(): Boolean = true

    companion object {
        private val THIRD_POINTS = listOf(
            0.333f to 0.333f,
            0.667f to 0.333f,
            0.333f to 0.667f,
            0.667f to 0.667f
        )
        // 对角线配对（索引）
        private val DIAGONAL_PAIRS = setOf(setOf(0, 3), setOf(1, 2))

        private const val GOOD_THRESHOLD = 0.08f
        private const val SOFT_THRESHOLD = 0.15f
    }

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val subjects = context.subjects

        val (score, guidance) = when {
            subjects.isEmpty() -> analyzeNoSubject()
            subjects.size == 1 -> analyzeSingle(subjects.first())
            subjects.size == 2 -> analyzeTwo(subjects[0], subjects[1])
            else -> analyzeMultiple(subjects)
        }

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = score, description = "构图评分"
            )),
            guidances = context.guidances + guidance
        )
    }

    // ── 0 个主体 ──

    private fun analyzeNoSubject(): Pair<Int, Guidance> {
        return 50 to Guidance("将主体对准网格交叉点", AdjustDirection.NONE, 100, id)
    }

    // ── 1 个主体：三分法 ──

    private fun analyzeSingle(subject: DetectedSubject): Pair<Int, Guidance> {
        val (nearest, distance) = findNearestThirdPoint(subject.centerX, subject.centerY)
        val score = (sqrt((1f - (distance / 0.5f).coerceIn(0f, 1f))) * 100).toInt()
        val guidance = singleSubjectGuidance(subject.centerX, subject.centerY, nearest, distance)
        return score to guidance
    }

    private fun singleSubjectGuidance(
        sx: Float, sy: Float, nearest: Pair<Float, Float>, distance: Float
    ): Guidance {
        if (distance <= GOOD_THRESHOLD) {
            return Guidance("构图很棒！保持不动", AdjustDirection.NONE, 100, id)
        }
        if (distance <= SOFT_THRESHOLD) {
            return Guidance("构图不错，微调一下更好", AdjustDirection.NONE, 50, id)
        }
        val dx = nearest.first - sx
        val dy = nearest.second - sy
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) Guidance("主体可以再靠右一些", AdjustDirection.MOVE_RIGHT, 10, id)
            else Guidance("主体可以再靠左一些", AdjustDirection.MOVE_LEFT, 10, id)
        } else {
            if (dy > 0) Guidance("主体可以再靠下一些", AdjustDirection.MOVE_DOWN, 10, id)
            else Guidance("主体可以再靠上一些", AdjustDirection.MOVE_UP, 10, id)
        }
    }

    // ── 2 个主体：对称/对角平衡 ──

    private fun analyzeTwo(s1: DetectedSubject, s2: DetectedSubject): Pair<Int, Guidance> {
        // 重心平衡评分（满分 50）
        val comX = (s1.centerX + s2.centerX) / 2f
        val comY = (s1.centerY + s2.centerY) / 2f
        val balanceDist = sqrt((comX - 0.5f).let { it * it } + (comY - 0.5f).let { it * it })
        val balanceScore = ((1f - (balanceDist / 0.35f).coerceIn(0f, 1f)) * 50).toInt()

        // 各自定位评分（满分 50）
        val (idx1, d1) = findNearestThirdPointIndex(s1.centerX, s1.centerY)
        val (idx2, d2) = findNearestThirdPointIndex(s2.centerX, s2.centerY)
        val onDifferentPoints = idx1 != idx2
        val avgDist = (d1 + d2) / 2f

        var placementScore = ((1f - (avgDist / 0.2f).coerceIn(0f, 1f)) * 30).toInt()
        if (onDifferentPoints) placementScore += 10
        if (onDifferentPoints && setOf(idx1, idx2) in DIAGONAL_PAIRS) placementScore += 10
        placementScore = placementScore.coerceIn(0, 50)

        val totalScore = (balanceScore + placementScore).coerceIn(0, 100)

        // 引导生成
        val guidance = when {
            balanceDist > 0.15f -> {
                val dx = 0.5f - comX
                val dy = 0.5f - comY
                val dir = dominantDirection(dx, dy)
                Guidance("整体构图${directionLabel(dir)}了，可以调整平衡", dir, 10, id)
            }
            !onDifferentPoints -> {
                Guidance("两个主体可以拉开一些距离", AdjustDirection.NONE, 10, id)
            }
            totalScore >= 80 -> {
                Guidance("双人构图很棒！", AdjustDirection.NONE, 100, id)
            }
            else -> {
                Guidance("双人构图不错，微调一下更好", AdjustDirection.NONE, 50, id)
            }
        }

        return totalScore to guidance
    }

    // ── 3+ 个主体：分布均匀度 ──

    private fun analyzeMultiple(subjects: List<DetectedSubject>): Pair<Int, Guidance> {
        // 重心平衡（满分 50）
        val comX = subjects.map { it.centerX }.average().toFloat()
        val comY = subjects.map { it.centerY }.average().toFloat()
        val balanceDist = sqrt((comX - 0.5f).let { it * it } + (comY - 0.5f).let { it * it })
        val balanceScore = ((1f - (balanceDist / 0.35f).coerceIn(0f, 1f)) * 50).toInt()

        // 分散度（满分 50）
        val xs = subjects.map { it.centerX }
        val ys = subjects.map { it.centerY }
        val stdX = stddev(xs)
        val stdY = stddev(ys)
        val avgStd = (stdX + stdY) / 2f
        val spreadScore = ((1f - (abs(avgStd - 0.20f) / 0.15f).coerceIn(0f, 1f)) * 50).toInt()

        val totalScore = (balanceScore + spreadScore).coerceIn(0, 100)

        val guidance = when {
            balanceDist > 0.15f -> {
                val dx = 0.5f - comX
                val dy = 0.5f - comY
                val dir = dominantDirection(dx, dy)
                Guidance("整体构图${directionLabel(dir)}了，可以调整平衡", dir, 10, id)
            }
            avgStd < 0.08f -> {
                Guidance("主体太集中，可以拉开构图", AdjustDirection.ZOOM_OUT, 10, id)
            }
            avgStd > 0.32f -> {
                Guidance("主体太分散，可以收紧构图", AdjustDirection.ZOOM_IN, 10, id)
            }
            totalScore >= 80 -> {
                Guidance("构图分布均匀！", AdjustDirection.NONE, 100, id)
            }
            else -> {
                Guidance("多主体构图不错，微调一下更好", AdjustDirection.NONE, 50, id)
            }
        }

        return totalScore to guidance
    }

    // ── 工具方法 ──

    private fun findNearestThirdPoint(x: Float, y: Float): Pair<Pair<Float, Float>, Float> {
        return THIRD_POINTS.map { point ->
            val dx = x - point.first
            val dy = y - point.second
            point to sqrt(dx * dx + dy * dy)
        }.minBy { it.second }
    }

    private fun findNearestThirdPointIndex(x: Float, y: Float): Pair<Int, Float> {
        return THIRD_POINTS.mapIndexed { index, point ->
            val dx = x - point.first
            val dy = y - point.second
            index to sqrt(dx * dx + dy * dy)
        }.minBy { it.second }
    }

    /** 选择偏移量较大的轴方向 */
    private fun dominantDirection(dx: Float, dy: Float): AdjustDirection {
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) AdjustDirection.MOVE_RIGHT else AdjustDirection.MOVE_LEFT
        } else {
            if (dy > 0) AdjustDirection.MOVE_DOWN else AdjustDirection.MOVE_UP
        }
    }

    /** 方向对应的中文偏移描述 */
    private fun directionLabel(dir: AdjustDirection): String {
        return when (dir) {
            AdjustDirection.MOVE_LEFT -> "偏右"
            AdjustDirection.MOVE_RIGHT -> "偏左"
            AdjustDirection.MOVE_UP -> "偏下"
            AdjustDirection.MOVE_DOWN -> "偏上"
            else -> ""
        }
    }

    private fun stddev(values: List<Float>): Float {
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean).let { d -> d * d } }.average().toFloat()
        return sqrt(variance)
    }
}
