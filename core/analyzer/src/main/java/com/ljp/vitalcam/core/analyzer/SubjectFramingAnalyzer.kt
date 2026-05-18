package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CameraMode
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
 * 场景化取景分析器。
 * 根据拍照模式检查主体大小、留白、填充等取景要素，
 * 无主体时不产出评分（避免稀释总分）。
 */
class SubjectFramingAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "subject_framing"
    override val order: Int = 500
    override fun isEnabled(): Boolean = true

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        if (context.subjects.isEmpty()) return context

        val result = when (context.cameraMode) {
            CameraMode.PORTRAIT -> analyzePortrait(context.subjects)
            CameraMode.FOOD -> analyzeFood(context.subjects)
            CameraMode.MACRO -> analyzeMacro(context.subjects)
            CameraMode.AUTO -> analyzeAuto(context.subjects)
            CameraMode.LANDSCAPE -> return context
        }

        val (score, guidance) = result

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = score, description = "取景评分"
            )),
            guidances = context.guidances + guidance
        )
    }

    // ── 人像模式：头部留白 + 主体大小 ──

    private fun analyzePortrait(subjects: List<DetectedSubject>): Pair<Int, Guidance> {
        val person = subjects.maxBy { it.confidence }

        // 头部留白：主体上边缘到画面顶部
        val headroom = (person.centerY - person.height / 2f).coerceAtLeast(0f)
        val headroomScore = when {
            headroom in 0.05f..0.15f -> 50
            headroom in 0.02f..0.05f || headroom in 0.15f..0.20f -> 35
            else -> 15
        }

        // 主体大小：人物高度占画面比例
        val subjectHeight = person.height
        val sizeScore = when {
            subjectHeight in 0.30f..0.70f -> 50
            subjectHeight in 0.20f..0.30f || subjectHeight in 0.70f..0.80f -> 35
            else -> 10
        }

        val totalScore = headroomScore + sizeScore

        // 选择最紧急的问题作为引导（大小问题优先于留白）
        val guidance = when {
            subjectHeight < 0.20f ->
                Guidance("人物太小，可以靠近一些", AdjustDirection.ZOOM_IN, 5, id)
            subjectHeight > 0.80f ->
                Guidance("人物太近，可以退后一点", AdjustDirection.ZOOM_OUT, 5, id)
            headroom < 0.02f ->
                Guidance("人物头顶空间不足，可以向下移一点", AdjustDirection.MOVE_DOWN, 5, id)
            headroom > 0.20f ->
                Guidance("头顶留白过多，可以向上移或靠近一些", AdjustDirection.MOVE_UP, 5, id)
            totalScore >= 80 ->
                Guidance("人像取景很好！", AdjustDirection.NONE, 100, id)
            else ->
                Guidance("人像取景不错，稍微调整会更好", AdjustDirection.NONE, 20, id)
        }

        return totalScore to guidance
    }

    // ── 美食模式：填充画面 + 居中 ──

    private fun analyzeFood(subjects: List<DetectedSubject>): Pair<Int, Guidance> {
        // 计算所有食物主体的并集包围框
        val minX = subjects.minOf { it.centerX - it.width / 2f }
        val maxX = subjects.maxOf { it.centerX + it.width / 2f }
        val minY = subjects.minOf { it.centerY - it.height / 2f }
        val maxY = subjects.maxOf { it.centerY + it.height / 2f }
        val unionArea = (maxX - minX).coerceAtLeast(0f) * (maxY - minY).coerceAtLeast(0f)

        val fillScore = when {
            unionArea >= 0.30f -> 50
            unionArea >= 0.15f -> 35
            else -> 15
        }

        // 美食适合居中构图
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerOffset = sqrt((centerX - 0.5f).let { it * it } + (centerY - 0.5f).let { it * it })
        val centerScore = when {
            centerOffset <= 0.08f -> 50
            centerOffset <= 0.15f -> 35
            else -> 15
        }

        val totalScore = fillScore + centerScore

        val guidance = when {
            unionArea < 0.15f ->
                Guidance("食物太小，可以靠近一些", AdjustDirection.ZOOM_IN, 5, id)
            unionArea < 0.30f && centerOffset <= 0.15f ->
                Guidance("再靠近一点会更好", AdjustDirection.ZOOM_IN, 20, id)
            centerOffset > 0.15f -> {
                val dx = 0.5f - centerX
                val dy = 0.5f - centerY
                val dir = if (abs(dx) > abs(dy)) {
                    if (dx > 0) AdjustDirection.MOVE_RIGHT else AdjustDirection.MOVE_LEFT
                } else {
                    if (dy > 0) AdjustDirection.MOVE_DOWN else AdjustDirection.MOVE_UP
                }
                Guidance("食物可以再居中一些", dir, 20, id)
            }
            totalScore >= 80 ->
                Guidance("美食取景很棒！", AdjustDirection.NONE, 100, id)
            else ->
                Guidance("美食取景不错", AdjustDirection.NONE, 20, id)
        }

        return totalScore to guidance
    }

    // ── 微距模式：填充画面 ──

    private fun analyzeMacro(subjects: List<DetectedSubject>): Pair<Int, Guidance> {
        val subject = subjects.first()
        val area = subject.width * subject.height

        val score = when {
            area >= 0.25f -> 90
            area >= 0.10f -> 60
            else -> 30
        }

        val guidance = when {
            area < 0.10f ->
                Guidance("微距拍摄需要更近一些", AdjustDirection.ZOOM_IN, 5, id)
            area < 0.25f ->
                Guidance("可以再靠近一点", AdjustDirection.ZOOM_IN, 20, id)
            else ->
                Guidance("微距取景很好！", AdjustDirection.NONE, 100, id)
        }

        return score to guidance
    }

    // ── 自动模式：基本大小检查 ──

    private fun analyzeAuto(subjects: List<DetectedSubject>): Pair<Int, Guidance> {
        val main = subjects.maxBy { it.confidence }
        val area = main.width * main.height

        return if (area < 0.05f) {
            30 to Guidance("主体太小，可以靠近一些", AdjustDirection.ZOOM_IN, 5, id)
        } else {
            80 to Guidance("取景良好", AdjustDirection.NONE, 100, id)
        }
    }
}
