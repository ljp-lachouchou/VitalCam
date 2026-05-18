package com.ljp.vitalcam.core.analyzer

import android.graphics.Bitmap
import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject
import kotlin.math.abs

/**
 * 风景模式专属分析器（order=500）。
 * 仅在 LANDSCAPE 模式下执行，评估三个维度：
 * 1. 天际线位置（40分）：理想在画面 1/3 或 2/3 处
 * 2. 水平度（30分）：左右亮度剖面差异判断倾斜
 * 3. 层次感（30分）：上中下三区色彩多样性
 */
class LandscapeAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "landscape_framing"
    override val order: Int = 500
    override fun isEnabled(): Boolean = true

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        if (context.cameraMode != CameraMode.LANDSCAPE) return context

        val bitmap = frame.bitmap
        val w = bitmap.width
        val h = bitmap.height

        // 逐行采样亮度（stride-4 加速）
        val rowBrightness = sampleRowBrightness(bitmap, w, h)

        val horizonResult = analyzeHorizon(rowBrightness, h)
        val levelResult = analyzeLevel(bitmap, w, h)
        val layerResult = analyzeLayers(rowBrightness, h)

        val totalScore = (horizonResult.score + levelResult.score + layerResult.score)
            .coerceIn(0, 100)

        // 取最紧急的引导（得分最低的维度）
        val worstResult = listOf(horizonResult, levelResult, layerResult).minBy { it.score }
        val guidance = if (totalScore >= 80) {
            Guidance("风景构图很好！", AdjustDirection.NONE, 100, id)
        } else {
            worstResult.guidance
        }

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = totalScore, description = "风景取景评分"
            )),
            guidances = context.guidances + guidance
        )
    }

    private data class SubScore(val score: Int, val guidance: Guidance)

    // ── 天际线位置（40分）──

    private fun analyzeHorizon(rowBrightness: FloatArray, h: Int): SubScore {
        // 找亮度变化最大的行作为天际线估计
        var maxDelta = 0f
        var horizonRow = h / 2
        for (i in 1 until rowBrightness.size) {
            val delta = abs(rowBrightness[i] - rowBrightness[i - 1])
            if (delta > maxDelta) {
                maxDelta = delta
                horizonRow = i
            }
        }

        val horizonRatio = horizonRow.toFloat() / h

        // 理想位置：1/3 或 2/3
        val distToThird = minOf(
            abs(horizonRatio - 0.333f),
            abs(horizonRatio - 0.667f)
        )

        val score = when {
            distToThird <= 0.05f -> 40
            distToThird <= 0.10f -> 30
            distToThird <= 0.15f -> 20
            else -> 10
        }

        val guidance = when {
            abs(horizonRatio - 0.5f) < 0.08f ->
                Guidance("天际线在正中间，可以上移或下移到三分线", AdjustDirection.NONE, 8, id)
            horizonRatio < 0.25f ->
                Guidance("天空占比太少，可以向上仰一些", AdjustDirection.MOVE_UP, 8, id)
            horizonRatio > 0.75f ->
                Guidance("地面占比太少，可以向下俯一些", AdjustDirection.MOVE_DOWN, 8, id)
            else ->
                Guidance("天际线位置不错", AdjustDirection.NONE, 100, id)
        }

        return SubScore(score, guidance)
    }

    // ── 水平度（30分）──

    private fun analyzeLevel(bitmap: Bitmap, w: Int, h: Int): SubScore {
        // 比较左侧 1/4 和右侧 1/4 的平均亮度剖面
        val leftCol = w / 4
        val rightCol = w * 3 / 4
        val sampleRows = 8
        val step = h / sampleRows

        var leftSum = 0f
        var rightSum = 0f

        for (i in 0 until sampleRows) {
            val y = (i * step).coerceIn(0, h - 1)
            val leftPixel = bitmap.getPixel(leftCol, y)
            val rightPixel = bitmap.getPixel(rightCol, y)
            leftSum += luminance(leftPixel)
            rightSum += luminance(rightPixel)
        }

        // 如果左右亮度整体差异大，配合行偏移来判断倾斜
        // 简化实现：用上半部分和下半部分的左右差异方向是否一致来判断
        val topLeftBright = luminance(bitmap.getPixel(leftCol, h / 4))
        val topRightBright = luminance(bitmap.getPixel(rightCol, h / 4))
        val botLeftBright = luminance(bitmap.getPixel(leftCol, h * 3 / 4))
        val botRightBright = luminance(bitmap.getPixel(rightCol, h * 3 / 4))

        // 交叉差异：如果上左亮+下右亮（或反之），说明有倾斜
        val crossDiff = abs((topLeftBright - topRightBright) - (botLeftBright - botRightBright))

        val score = when {
            crossDiff < 10f -> 30   // 很水平
            crossDiff < 25f -> 20   // 轻微倾斜
            crossDiff < 40f -> 12   // 明显倾斜
            else -> 5               // 严重倾斜
        }

        val guidance = if (crossDiff >= 25f) {
            // 判断倾斜方向
            if (topLeftBright > topRightBright) {
                Guidance("画面向右倾斜了，可以顺时针调整", AdjustDirection.TILT_CW, 8, id)
            } else {
                Guidance("画面向左倾斜了，可以逆时针调整", AdjustDirection.TILT_CCW, 8, id)
            }
        } else {
            Guidance("水平线很正", AdjustDirection.NONE, 100, id)
        }

        return SubScore(score, guidance)
    }

    // ── 层次感（30分）──

    private fun analyzeLayers(rowBrightness: FloatArray, h: Int): SubScore {
        // 将画面分为上中下三区，计算各区平均亮度
        val thirdH = h / 3
        val topAvg = rowBrightness.take(thirdH).average().toFloat()
        val midAvg = rowBrightness.drop(thirdH).take(thirdH).average().toFloat()
        val botAvg = rowBrightness.drop(thirdH * 2).average().toFloat()

        // 三区亮度差异越大，层次感越好
        val variation = maxOf(
            abs(topAvg - midAvg),
            abs(midAvg - botAvg),
            abs(topAvg - botAvg)
        )

        val score = when {
            variation >= 30f -> 30   // 层次分明
            variation >= 15f -> 20   // 有一定层次
            else -> 10               // 整体平坦
        }

        val guidance = if (variation < 15f) {
            Guidance("画面层次感不足，可以纳入前景增加纵深", AdjustDirection.NONE, 8, id)
        } else {
            Guidance("画面层次丰富", AdjustDirection.NONE, 100, id)
        }

        return SubScore(score, guidance)
    }

    // ── 工具方法 ──

    /** 逐行采样亮度，stride-4 水平采样加速 */
    private fun sampleRowBrightness(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val result = FloatArray(h)
        val stride = 4
        val samplesPerRow = w / stride

        for (y in 0 until h) {
            var sum = 0f
            for (x in 0 until w step stride) {
                sum += luminance(bitmap.getPixel(x, y))
            }
            result[y] = sum / samplesPerRow
        }
        return result
    }

    /** 像素亮度（加权灰度） */
    private fun luminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
