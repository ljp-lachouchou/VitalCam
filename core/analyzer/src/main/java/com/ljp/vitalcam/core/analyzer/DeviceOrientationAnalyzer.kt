package com.ljp.vitalcam.core.analyzer

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
 * 设备姿态分析（order=100，所有图像分析之前）。
 * 利用传感器的 roll/pitch 角度评估画面水平度和拍摄角度。
 */
class DeviceOrientationAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "orientation"
    override val order: Int = 100
    override fun isEnabled(): Boolean = true

    companion object {
        private const val SOURCE_ID = "orientation"

        // Roll 阈值（度）
        private const val ROLL_LEVEL = 3f
        private const val ROLL_SLIGHT = 15f
        private const val ROLL_STRONG = 35f
        private const val ROLL_DUTCH = 55f

        // 各模式的理想 pitch 和容差
        private data class PitchProfile(
            val idealPitch: Float,
            val tolerance: Float,
            val styleName: String
        )

        private val PITCH_PROFILES = mapOf(
            CameraMode.FOOD to PitchProfile(-45f, 20f, "美食"),
            CameraMode.PORTRAIT to PitchProfile(0f, 15f, "人像"),
            CameraMode.LANDSCAPE to PitchProfile(0f, 20f, "风景"),
            CameraMode.MACRO to PitchProfile(-30f, 20f, "微距"),
        )
    }

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val roll = frame.rollDegrees
        val pitch = frame.pitchDegrees
        val guidances = mutableListOf<Guidance>()

        // ── Roll 评分与引导 ──
        val absRoll = abs(roll)
        val rollScore = when {
            absRoll <= ROLL_LEVEL -> 100
            absRoll > ROLL_DUTCH -> 100  // 接近横拍，不扣分
            else -> (100 - ((absRoll - ROLL_LEVEL) * 3).toInt()).coerceAtLeast(30)
        }

        val tiltDirection = if (roll > 0) AdjustDirection.TILT_CCW else AdjustDirection.TILT_CW

        when {
            absRoll <= ROLL_LEVEL -> { /* 水平，不提示 */ }
            absRoll <= ROLL_SLIGHT -> guidances += Guidance(
                message = "画面略微倾斜，建议保持水平",
                direction = tiltDirection,
                priority = 10,
                sourceId = SOURCE_ID
            )
            absRoll <= ROLL_STRONG -> guidances += Guidance(
                message = "画面倾斜明显，请调整手机角度",
                direction = tiltDirection,
                priority = 5,
                sourceId = SOURCE_ID
            )
            absRoll <= ROLL_DUTCH -> guidances += Guidance(
                message = "接近45°倾斜构图",
                direction = AdjustDirection.NONE,
                priority = 30,
                sourceId = SOURCE_ID
            )
            // > ROLL_DUTCH: 可能有意横拍，不提示
        }

        // ── Pitch 评分与引导 ──
        val pitchProfile = PITCH_PROFILES[context.cameraMode]
        val pitchScore: Int
        if (pitchProfile != null) {
            val deviation = abs(pitch - pitchProfile.idealPitch)
            pitchScore = if (deviation <= pitchProfile.tolerance) {
                100
            } else {
                (100 - ((deviation - pitchProfile.tolerance) * 2).toInt()).coerceAtLeast(40)
            }

            if (deviation > pitchProfile.tolerance) {
                val suggestion = buildPitchSuggestion(pitchProfile, pitch)
                guidances += Guidance(
                    message = suggestion,
                    direction = AdjustDirection.NONE,
                    priority = 15,
                    sourceId = SOURCE_ID
                )
            }
        } else {
            // AUTO 模式：仅做风格信息展示，不评分
            pitchScore = 100
            val styleHint = pitchStyleHint(pitch)
            if (styleHint != null) {
                guidances += Guidance(
                    message = styleHint,
                    direction = AdjustDirection.NONE,
                    priority = 50,
                    sourceId = SOURCE_ID
                )
            }
        }

        val finalScore = minOf(rollScore, pitchScore)

        return context.copy(
            compositionScores = context.compositionScores + (SOURCE_ID to CompositionScore(
                ruleId = SOURCE_ID,
                score = finalScore,
                description = "设备姿态"
            )),
            guidances = context.guidances + guidances
        )
    }

    /** 根据模式生成 pitch 调整建议 */
    private fun buildPitchSuggestion(profile: PitchProfile, currentPitch: Float): String {
        val idealDesc = when {
            profile.idealPitch < -30f -> "俯拍"
            profile.idealPitch < -10f -> "略微俯拍"
            profile.idealPitch > 20f -> "仰拍"
            else -> "平拍"
        }
        val currentDesc = when {
            currentPitch < -50f -> "当前大角度俯拍"
            currentPitch < -20f -> "当前俯拍"
            currentPitch > 50f -> "当前大角度仰拍"
            currentPitch > 20f -> "当前仰拍"
            else -> "当前平拍"
        }
        return "${profile.styleName}建议${idealDesc}，${currentDesc}"
    }

    /** AUTO 模式下的角度风格提示 */
    private fun pitchStyleHint(pitch: Float): String? = when {
        pitch < -50f -> "大角度俯拍，适合俯瞰全景"
        pitch < -20f -> "俯拍角度，适合美食/桌面"
        pitch > 50f -> "大角度仰拍，适合天空/穹顶"
        pitch > 20f -> "仰拍角度，适合建筑/显高"
        else -> null
    }
}
