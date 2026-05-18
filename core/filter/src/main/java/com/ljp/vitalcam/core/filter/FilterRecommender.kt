package com.ljp.vitalcam.core.filter

import com.ljp.vitalcam.core.common.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 基于 AnalysisResult 推荐滤镜。
 * 返回排序后的模板列表，第一个为 AI 自动生成的最佳模板。
 */
@Singleton
class FilterRecommender @Inject constructor() {

    /**
     * 生成推荐列表：[AI自动, 预设1, 预设2, ...]
     * @param rollDegrees 拍照时的 roll 角度，用于自动水平矫正
     */
    fun recommend(analysisResult: AnalysisResult, rollDegrees: Float = 0f): List<FilterTemplate> {
        val aiTemplate = buildAiTemplate(analysisResult, rollDegrees)

        val scored = FilterPresets.ALL
            .map { it to scoreForPhoto(it, analysisResult) }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        return listOf(aiTemplate) + scored
    }

    /** 根据分析结果自动构建 AI 推荐模板 */
    private fun buildAiTemplate(result: AnalysisResult, rollDegrees: Float): FilterTemplate {
        val lightScore = result.dimensionScores["lighting"]?.score ?: 70
        val colorScore = result.dimensionScores["color"]?.score ?: 70

        // 亮度补偿
        val brightness = when {
            lightScore < 40 -> 0.15f
            lightScore < 60 -> 0.08f
            else -> 0f
        }

        // 对比度增强
        val contrast = when {
            lightScore < 50 -> 0.12f
            else -> 0.05f
        }

        // 饱和度补偿
        val saturation = when {
            colorScore < 40 -> 0.15f
            colorScore < 60 -> 0.08f
            else -> 0.03f
        }

        // 自动水平矫正（小角度偏斜才矫正）
        val autoLevel = if (abs(rollDegrees) > 1.5f && abs(rollDegrees) < 15f) {
            -rollDegrees
        } else 0f

        return FilterTemplate(
            id = "ai_auto",
            name = "AI 推荐",
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            vignette = 0.15f,
            autoLevelDegrees = autoLevel
        )
    }

    /** 评估预设模板与照片特征的匹配度 */
    private fun scoreForPhoto(template: FilterTemplate, result: AnalysisResult): Int {
        val lightScore = result.dimensionScores["lighting"]?.score ?: 50
        val colorScore = result.dimensionScores["color"]?.score ?: 50
        var score = 50

        when (template.id) {
            "warm" -> {
                if (colorScore < 60) score += 20
            }
            "cool" -> {
                if (colorScore > 70) score += 15
            }
            "fresh" -> {
                if (lightScore > 70) score += 20
                if (colorScore < 60) score += 10
            }
            "vintage" -> {
                score += 10
            }
            "cinematic" -> {
                if (lightScore > 60) score += 15
            }
            "bw" -> {
                // 色彩已经很好时不推荐黑白
                if (colorScore > 80) score -= 10
                if (colorScore < 40) score += 15
            }
        }

        return score
    }
}
