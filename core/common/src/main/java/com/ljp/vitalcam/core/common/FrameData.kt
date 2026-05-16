package com.ljp.vitalcam.core.common

import android.graphics.Bitmap

/** 封装相机帧数据，作为分析管道的输入 */
data class FrameData(
    /** 帧图像 */
    val bitmap: Bitmap,
    /** 图像宽度 */
    val width: Int,
    /** 图像高度 */
    val height: Int,
    /** 传感器旋转角度 */
    val rotationDegrees: Int,
    /** 帧时间戳（毫秒） */
    val timestampMs: Long
)
