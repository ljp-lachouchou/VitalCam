package com.ljp.vitalcam.core.common

/** 检测到的主体（人脸/物体等） */
data class DetectedSubject(
    /** 主体中心 X 坐标（归一化 0.0~1.0） */
    val centerX: Float,
    /** 主体中心 Y 坐标（归一化 0.0~1.0） */
    val centerY: Float,
    /** 主体宽度（归一化） */
    val width: Float,
    /** 主体高度（归一化） */
    val height: Float,
    /** 标签（如 "person", "face"） */
    val label: String,
    /** 置信度 0.0~1.0 */
    val confidence: Float
)

/** 场景类型枚举 */
enum class SceneType {
    PORTRAIT,
    LANDSCAPE,
    FOOD,
    MACRO,
    ARCHITECTURE,
    UNKNOWN
}

/** 单条构图规则的评估得分 */
data class CompositionScore(
    /** 规则标识 */
    val ruleId: String,
    /** 得分 0~100 */
    val score: Int,
    /** 规则描述 */
    val description: String
)

/** 拍照引导建议 */
data class Guidance(
    /** 引导文字 */
    val message: String,
    /** 调整方向 */
    val direction: AdjustDirection,
    /** 优先级，值越小优先级越高 */
    val priority: Int
)

/** 调整方向枚举 */
enum class AdjustDirection {
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,
    TILT_CW,
    TILT_CCW,
    ZOOM_IN,
    ZOOM_OUT,
    NONE
}
