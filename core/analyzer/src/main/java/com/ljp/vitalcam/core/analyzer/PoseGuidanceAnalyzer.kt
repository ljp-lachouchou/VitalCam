package com.ljp.vitalcam.core.analyzer

import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.CompositionScore
import com.ljp.vitalcam.core.common.FrameData
import com.ljp.vitalcam.core.common.Guidance
import com.ljp.vitalcam.core.common.PersonPose
import com.ljp.vitalcam.core.pipeline.AnalysisContext
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import javax.inject.Inject
import kotlin.math.abs

/**
 * 人像姿势引导分析器（order=600）。
 * 基于 PoseLandmarker 检测到的关键点，运行 5 条规则：
 * 身体角度、手臂位置、头部倾斜、下巴高度、重心分布。
 * 仅当 poseLandmarks 非空时产出评分和引导。
 */
class PoseGuidanceAnalyzer @Inject constructor() : AnalysisStep {

    override val id: String = "pose_guidance"
    override val order: Int = 600
    override fun isEnabled(): Boolean = true

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        if (context.poseLandmarks.isEmpty()) return context

        val pose = context.poseLandmarks.first()
        if (pose.landmarks.size < 33) return context

        val rules = listOf(
            checkBodyAngle(pose),
            checkArmPosition(pose),
            checkHeadTilt(pose),
            checkChinHeight(pose),
            checkWeightDistribution(pose)
        )

        val totalScore = rules.sumOf { it.score }.coerceIn(0, 100)

        // 取得分最低的规则作为最紧急的引导
        val worstRule = rules.minBy { it.score }
        val guidance = if (worstRule.score >= 15) {
            Guidance("姿势很棒！", AdjustDirection.NONE, 100, id)
        } else {
            worstRule.guidance
        }

        return context.copy(
            compositionScores = context.compositionScores + (id to CompositionScore(
                ruleId = id, score = totalScore, description = "姿势评分"
            )),
            guidances = context.guidances + guidance
        )
    }

    private data class RuleResult(val score: Int, val guidance: Guidance)

    // ── 规则 1: 身体角度（肩宽与臀宽比判断侧身程度）──

    private fun checkBodyAngle(pose: PersonPose): RuleResult {
        val ls = pose.get(PersonPose.LEFT_SHOULDER)
        val rs = pose.get(PersonPose.RIGHT_SHOULDER)
        val lh = pose.get(PersonPose.LEFT_HIP)
        val rh = pose.get(PersonPose.RIGHT_HIP)

        val shoulderWidth = abs(ls.x - rs.x)
        val hipWidth = abs(lh.x - rh.x).coerceAtLeast(0.01f)
        val ratio = shoulderWidth / hipWidth

        // ratio 大说明正面朝向相机，ratio 小说明侧身
        val score = when {
            ratio in 0.60f..0.85f -> 20  // 适度侧身
            ratio in 0.85f..0.95f -> 15  // 轻微侧身
            ratio > 0.95f && shoulderWidth > 0.25f -> 5  // 完全正面且宽
            else -> 15  // 过度侧身或肩宽太窄（可能距离远）
        }

        val guidance = when {
            ratio > 0.95f && shoulderWidth > 0.25f ->
                Guidance("可以稍微侧身，会更显瘦", AdjustDirection.NONE, 8, id)
            else ->
                Guidance("身体角度不错", AdjustDirection.NONE, 100, id)
        }

        return RuleResult(score, guidance)
    }

    // ── 规则 2: 手臂位置（肘部与肩部的水平间距）──

    private fun checkArmPosition(pose: PersonPose): RuleResult {
        val ls = pose.get(PersonPose.LEFT_SHOULDER)
        val rs = pose.get(PersonPose.RIGHT_SHOULDER)
        val le = pose.get(PersonPose.LEFT_ELBOW)
        val re = pose.get(PersonPose.RIGHT_ELBOW)

        val leftGap = abs(le.x - ls.x)
        val rightGap = abs(re.x - rs.x)
        val avgGap = (leftGap + rightGap) / 2f

        val score = when {
            avgGap in 0.03f..0.15f -> 20  // 手臂自然离开身体
            avgGap < 0.03f -> 5           // 手臂贴紧身体
            else -> 12                     // 手臂过度张开
        }

        val guidance = when {
            avgGap < 0.03f ->
                Guidance("手臂可以稍微离开身体一点，姿势会更自然", AdjustDirection.NONE, 8, id)
            avgGap > 0.20f ->
                Guidance("手臂可以稍微放松一些", AdjustDirection.NONE, 8, id)
            else ->
                Guidance("手臂姿势自然", AdjustDirection.NONE, 100, id)
        }

        return RuleResult(score, guidance)
    }

    // ── 规则 3: 头部倾斜（左右耳朵高度差）──

    private fun checkHeadTilt(pose: PersonPose): RuleResult {
        val leftEar = pose.get(PersonPose.LEFT_EAR)
        val rightEar = pose.get(PersonPose.RIGHT_EAR)

        // 仅当两个耳朵都可见时检查
        if (leftEar.visibility < 0.5f && rightEar.visibility < 0.5f) {
            return RuleResult(15, Guidance("头部姿势正常", AdjustDirection.NONE, 100, id))
        }

        val tilt = abs(leftEar.y - rightEar.y)

        val score = when {
            tilt <= 0.005f -> 18          // 完全正面
            tilt in 0.005f..0.04f -> 20   // 微微倾斜（好看）
            else -> 5                      // 倾斜过大
        }

        val guidance = if (tilt > 0.04f) {
            Guidance("头部可以稍微摆正一点", AdjustDirection.NONE, 8, id)
        } else {
            Guidance("头部角度很好", AdjustDirection.NONE, 100, id)
        }

        return RuleResult(score, guidance)
    }

    // ── 规则 4: 下巴高度（鼻子相对肩膀的垂直距离）──

    private fun checkChinHeight(pose: PersonPose): RuleResult {
        val nose = pose.get(PersonPose.NOSE)
        val ls = pose.get(PersonPose.LEFT_SHOULDER)
        val rs = pose.get(PersonPose.RIGHT_SHOULDER)

        val shoulderAvgY = (ls.y + rs.y) / 2f
        // 正值=鼻子在肩膀下方，负值=鼻子在肩膀上方（正常情况）
        val chinHeight = nose.y - shoulderAvgY

        val score = when {
            chinHeight in -0.08f..0.02f -> 20  // 正常范围
            chinHeight < -0.08f -> 8           // 下巴抬太高
            else -> 8                           // 下巴压太低
        }

        val guidance = when {
            chinHeight < -0.08f ->
                Guidance("下巴可以稍微收一点", AdjustDirection.NONE, 8, id)
            chinHeight > 0.02f ->
                Guidance("下巴可以稍微抬一点", AdjustDirection.NONE, 8, id)
            else ->
                Guidance("下巴位置很好", AdjustDirection.NONE, 100, id)
        }

        return RuleResult(score, guidance)
    }

    // ── 规则 5: 重心分布（膝盖高度不对称 → 重心偏移 → 更放松的姿态）──

    private fun checkWeightDistribution(pose: PersonPose): RuleResult {
        val lk = pose.get(PersonPose.LEFT_KNEE)
        val rk = pose.get(PersonPose.RIGHT_KNEE)

        // 仅当两个膝盖都可见时检查
        if (lk.visibility < 0.5f || rk.visibility < 0.5f) {
            return RuleResult(15, Guidance("站姿正常", AdjustDirection.NONE, 100, id))
        }

        val kneeYDiff = abs(lk.y - rk.y)

        val score = when {
            kneeYDiff >= 0.01f -> 20  // 重心有偏移，姿势放松
            else -> 8                  // 双腿完全对称，较僵硬
        }

        val guidance = if (kneeYDiff < 0.01f) {
            Guidance("重心可以稍微放在一条腿上，姿势更放松", AdjustDirection.NONE, 8, id)
        } else {
            Guidance("重心分布自然", AdjustDirection.NONE, 100, id)
        }

        return RuleResult(score, guidance)
    }
}
