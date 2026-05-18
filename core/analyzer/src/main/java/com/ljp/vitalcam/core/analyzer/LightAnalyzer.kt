package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * 光线分析器（order=700）。
 * 通过 stride-4 像素采样，单遍计算亮度、对比度、逆光、均匀度四项指标。
 * 将像素缓冲存入 metadata 供 ColorAnalyzer 复用。
 */
class LightAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "lighting"
    override val order: Int = 700
    override fun isEnabled(): Boolean = true

    companion object {
        private const val STRIDE = 4
        // metadata 键名
        const val KEY_PIXEL_BUFFER = "pixelBuffer"
        const val KEY_PIXEL_WIDTH = "pixelBufferWidth"
        const val KEY_PIXEL_HEIGHT = "pixelBufferHeight"
    }

    // 复用像素缓冲，避免逐帧分配
    private var pixelBuffer: IntArray? = null

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val bitmap = frame.bitmap
        val w = bitmap.width
        val h = bitmap.height
        val totalPixels = w * h

        // 分配或复用像素缓冲
        val pixels = pixelBuffer?.takeIf { it.size >= totalPixels } ?: IntArray(totalPixels)
        pixelBuffer = pixels
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 亮度直方图
        val histogram = IntArray(256)
        // 逆光检测：中心 vs 周边
        var centerSum = 0L
        var centerCount = 0
        var peripherySum = 0L
        var peripheryCount = 0
        // 均匀度：四象限
        val quadSums = LongArray(4)
        val quadCounts = IntArray(4)

        val xCenterStart = w / 4
        val xCenterEnd = w * 3 / 4
        val yCenterStart = h / 4
        val yCenterEnd = h * 3 / 4
        val halfW = w / 2
        val halfH = h / 2

        var sampleCount = 0

        // 单遍 stride-4 扫描
        var py = 0
        while (py < h) {
            var px = 0
            while (px < w) {
                val pixel = pixels[py * w + px]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (299 * r + 587 * g + 114 * b) / 1000

                histogram[luma]++
                sampleCount++

                // 逆光：中心 vs 周边
                if (px in xCenterStart until xCenterEnd && py in yCenterStart until yCenterEnd) {
                    centerSum += luma
                    centerCount++
                } else {
                    peripherySum += luma
                    peripheryCount++
                }

                // 四象限
                val qi = (if (px >= halfW) 1 else 0) + (if (py >= halfH) 2 else 0)
                quadSums[qi] += luma
                quadCounts[qi]++

                px += STRIDE
            }
            py += STRIDE
        }

        // ── 计算子分数 ──

        // 亮度
        val meanLuma = if (sampleCount > 0) {
            var lumaTotal = 0L
            for (i in 0 until 256) lumaTotal += i.toLong() * histogram[i]
            (lumaTotal / sampleCount).toInt()
        } else 128

        val brightnessScore = when (meanLuma) {
            in 90..170 -> 35
            in 70..89, in 171..200 -> 25
            in 50..69, in 201..220 -> 15
            else -> 5
        }

        // 对比度（p5/p95 百分位差）
        val p5Threshold = sampleCount * 5 / 100
        val p95Threshold = sampleCount * 95 / 100
        var cumulative = 0
        var p5 = 0
        var p95 = 255
        for (i in 0 until 256) {
            cumulative += histogram[i]
            if (cumulative >= p5Threshold && p5 == 0) p5 = i
            if (cumulative >= p95Threshold) { p95 = i; break }
        }
        val spread = p95 - p5

        val contrastScore = when {
            spread >= 120 -> 30
            spread >= 80 -> 22
            spread >= 40 -> 12
            else -> 4
        }

        // 逆光
        val centerMean = if (centerCount > 0) (centerSum / centerCount).toInt() else meanLuma
        val peripheryMean = if (peripheryCount > 0) (peripherySum / peripheryCount).toInt() else meanLuma
        val backlightDiff = peripheryMean - centerMean

        val backlightScore = when {
            backlightDiff <= 20 -> 20
            backlightDiff <= 50 -> 14
            backlightDiff <= 80 -> 7
            else -> 2
        }

        // 均匀度
        val quadMeans = FloatArray(4) { i ->
            if (quadCounts[i] > 0) quadSums[i].toFloat() / quadCounts[i] else meanLuma.toFloat()
        }
        val overallQuadMean = quadMeans.average().toFloat()
        val quadVariance = quadMeans.map { (it - overallQuadMean) * (it - overallQuadMean) }.average().toFloat()
        val quadStddev = sqrt(quadVariance)

        val evennessScore = when {
            quadStddev <= 15f -> 15
            quadStddev <= 30f -> 11
            quadStddev <= 50f -> 6
            else -> 2
        }

        val totalScore = (brightnessScore + contrastScore + backlightScore + evennessScore).coerceIn(0, 100)

        // ── 引导（按紧急度排列）──
        val guidance = when {
            meanLuma < 50 ->
                Guidance("画面太暗，请增加光线或调高曝光", AdjustDirection.NONE, 5, id)
            meanLuma > 220 ->
                Guidance("画面过曝，请降低曝光或避开强光", AdjustDirection.NONE, 5, id)
            backlightDiff > 80 ->
                Guidance("检测到逆光，建议调整拍摄角度", AdjustDirection.NONE, 5, id)
            meanLuma < 70 ->
                Guidance("画面偏暗，可以增加一些光线", AdjustDirection.NONE, 8, id)
            meanLuma > 200 ->
                Guidance("画面偏亮，可以降低一些曝光", AdjustDirection.NONE, 8, id)
            backlightDiff > 50 ->
                Guidance("背景比主体亮，注意逆光", AdjustDirection.NONE, 8, id)
            spread < 40 ->
                Guidance("画面对比度很低，光线较平", AdjustDirection.NONE, 10, id)
            quadStddev > 50f ->
                Guidance("光线不均匀，可以调整位置", AdjustDirection.NONE, 10, id)
            totalScore >= 80 ->
                Guidance("光线条件很好！", AdjustDirection.NONE, 100, id)
            else ->
                Guidance("光线条件一般，可以寻找更好的光源", AdjustDirection.NONE, 20, id)
        }

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = totalScore, description = "光线评分"
            )),
            guidances = context.guidances + guidance,
            metadata = context.metadata + mapOf(
                KEY_PIXEL_BUFFER to pixels,
                KEY_PIXEL_WIDTH to w,
                KEY_PIXEL_HEIGHT to h
            )
        )
    }
}
