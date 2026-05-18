package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * 色彩分析器（order=800）。
 * 通过 stride-4 像素采样，单遍计算饱和度、色温、色彩丰富度三项指标。
 * 优先从 metadata 复用 LightAnalyzer 的像素缓冲。
 */
class ColorAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "color"
    override val order: Int = 800
    override fun isEnabled(): Boolean = true

    companion object {
        private const val STRIDE = 4
        private const val HUE_BINS = 12
        // 色彩丰富度：仅统计有色度的像素
        private const val MIN_SAT_DELTA = 25   // maxC - minC > 25 才算有色度
        private const val MIN_BRIGHTNESS = 30   // maxC > 30 才算有亮度
    }

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val bitmap = frame.bitmap
        val w: Int
        val h: Int
        val pixels: IntArray

        // 从 metadata 复用 LightAnalyzer 的像素缓冲
        val cachedPixels = context.metadata[LightAnalyzer.KEY_PIXEL_BUFFER] as? IntArray
        val cachedW = context.metadata[LightAnalyzer.KEY_PIXEL_WIDTH] as? Int
        val cachedH = context.metadata[LightAnalyzer.KEY_PIXEL_HEIGHT] as? Int

        if (cachedPixels != null && cachedW != null && cachedH != null) {
            pixels = cachedPixels
            w = cachedW
            h = cachedH
        } else {
            w = bitmap.width
            h = bitmap.height
            pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        }

        // 饱和度累积
        var satDeltaSum = 0L
        var maxCSum = 0L
        // 色温累积
        var rSum = 0L
        var bSum = 0L
        // 色相直方图
        val hueBins = IntArray(HUE_BINS)
        var chromaticCount = 0
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

                sampleCount++

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC

                satDeltaSum += delta
                maxCSum += maxC
                rSum += r
                bSum += b

                // 色相直方图：仅统计有色度且有亮度的像素
                if (delta > MIN_SAT_DELTA && maxC > MIN_BRIGHTNESS) {
                    val hue = when (maxC) {
                        r -> {
                            val h0 = 60 * (g - b) / delta
                            if (h0 < 0) h0 + 360 else h0
                        }
                        g -> 60 * (b - r) / delta + 120
                        else -> 60 * (r - g) / delta + 240
                    }
                    val bin = ((hue % 360) / 30).coerceIn(0, HUE_BINS - 1)
                    hueBins[bin]++
                    chromaticCount++
                }

                px += STRIDE
            }
            py += STRIDE
        }

        if (sampleCount == 0) return context

        // ── 饱和度（40 分）──
        val meanSat = if (maxCSum > 0) satDeltaSum.toFloat() / maxCSum else 0f

        val saturationScore = when {
            meanSat in 0.20f..0.55f -> 40
            meanSat in 0.15f..0.20f || meanSat in 0.55f..0.65f -> 30
            meanSat in 0.10f..0.15f || meanSat in 0.65f..0.75f -> 18
            else -> 8
        }

        // ── 色温（30 分）──
        val rMean = rSum.toFloat() / sampleCount
        val bMean = bSum.toFloat() / sampleCount
        val tempRatio = if (rMean > 0f) bMean / rMean else 1f

        val temperatureScore = when {
            tempRatio in 0.75f..1.25f -> 30
            tempRatio in 0.60f..0.75f || tempRatio in 1.25f..1.50f -> 22
            tempRatio in 0.45f..0.60f || tempRatio in 1.50f..1.80f -> 12
            else -> 5
        }

        // ── 色彩丰富度（30 分）──
        val activeThreshold = if (chromaticCount > 0) chromaticCount * 3 / 100 else 1
        val activeCount = hueBins.count { it >= activeThreshold }

        val varietyScore = when {
            activeCount in 3..6 -> 30
            activeCount == 2 || activeCount in 7..8 -> 22
            activeCount >= 9 -> 15
            else -> 12
        }

        val totalScore = (saturationScore + temperatureScore + varietyScore).coerceIn(0, 100)

        // ── 引导 ──
        val guidance = when {
            meanSat < 0.10f ->
                Guidance("画面色彩过于平淡，缺少颜色", AdjustDirection.NONE, 8, id)
            meanSat > 0.75f ->
                Guidance("色彩过度饱和，可能偏色", AdjustDirection.NONE, 8, id)
            tempRatio < 0.45f ->
                Guidance("画面偏暖/偏黄，注意白平衡", AdjustDirection.NONE, 8, id)
            tempRatio > 1.80f ->
                Guidance("画面偏冷/偏蓝，注意白平衡", AdjustDirection.NONE, 8, id)
            activeCount >= 9 ->
                Guidance("色彩较杂乱，可以简化背景", AdjustDirection.NONE, 10, id)
            totalScore >= 80 ->
                Guidance("色彩表现很好！", AdjustDirection.NONE, 100, id)
            else ->
                Guidance("色彩条件一般", AdjustDirection.NONE, 20, id)
        }

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = totalScore, description = "色彩评分"
            )),
            guidances = context.guidances + guidance
        )
    }
}
