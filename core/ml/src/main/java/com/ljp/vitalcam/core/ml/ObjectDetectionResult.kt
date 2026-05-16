package com.ljp.vitalcam.core.ml

import android.graphics.RectF

/** 目标检测推理结果 */
data class ObjectDetectionResult(
    /** 检测到的目标列表 */
    val detections: List<Detection>
) {
    companion object {
        val EMPTY = ObjectDetectionResult(detections = emptyList())
    }
}

/** 单个检测目标 */
data class Detection(
    /** 边界框（归一化坐标 0.0~1.0） */
    val boundingBox: RectF,
    /** 目标标签 */
    val label: String,
    /** 置信度 0.0~1.0 */
    val confidence: Float
)
