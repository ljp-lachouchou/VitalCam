package com.ljp.vitalcam.core.filter

/**
 * 滤镜模板：一组图像处理参数的命名组合。
 * 数值范围统一归一化，0 表示无变化。
 */
data class FilterTemplate(
    val id: String,
    val name: String,
    /** 亮度 -1.0~+1.0，0=不变 */
    val brightness: Float = 0f,
    /** 对比度 -1.0~+1.0，0=不变 */
    val contrast: Float = 0f,
    /** 饱和度 -1.0~+1.0，0=不变，-1.0=灰度 */
    val saturation: Float = 0f,
    /** 色温 -1.0(冷/蓝)~+1.0(暖/黄)，0=中性 */
    val warmth: Float = 0f,
    /** 色调偏移 -1.0(绿)~+1.0(品红)，0=中性 */
    val tint: Float = 0f,
    /** 暗角强度 0.0~1.0 */
    val vignette: Float = 0f,
    /** 锐化强度 0.0~1.0 */
    val sharpen: Float = 0f,
    /** 胶片颗粒强度 0.0~1.0 */
    val grain: Float = 0f,
    /** 水平矫正角度（度），0=不矫正 */
    val autoLevelDegrees: Float = 0f
)
