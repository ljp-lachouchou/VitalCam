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
    val timestampMs: Long,
    /** 设备 roll 角度（度）：绕视线轴旋转，0=水平，正值=顺时针倾斜 */
    val rollDegrees: Float = 0f,
    /** 设备 pitch 角度（度）：俯仰角，0=正对地平线，负值=俯拍，正值=仰拍 */
    val pitchDegrees: Float = 0f
)
