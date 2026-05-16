package com.ljp.vitalcam.core.ml

import android.graphics.Bitmap

/** ML 推理引擎抽象，支持替换不同后端（MediaPipe / TFLite / ONNX） */
interface MLRuntime {

    /** 引擎名称标识 */
    val name: String

    /** 引擎是否可用（模型已加载等） */
    fun isAvailable(): Boolean

    /** 执行目标检测推理 */
    suspend fun detectObjects(input: Bitmap): ObjectDetectionResult

    /** 释放资源 */
    fun close()
}
