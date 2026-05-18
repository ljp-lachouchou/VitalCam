package com.ljp.vitalcam.core.filter

/** 内置滤镜预设 */
object FilterPresets {

    val ORIGINAL = FilterTemplate(id = "original", name = "原片")

    val WARM = FilterTemplate(
        id = "warm", name = "暖调",
        warmth = 0.3f, saturation = 0.1f, contrast = 0.05f
    )

    val COOL = FilterTemplate(
        id = "cool", name = "冷调",
        warmth = -0.25f, saturation = 0.05f, contrast = 0.1f
    )

    val FRESH = FilterTemplate(
        id = "fresh", name = "清新",
        brightness = 0.1f, saturation = 0.2f, contrast = 0.05f, warmth = -0.05f
    )

    val VINTAGE = FilterTemplate(
        id = "vintage", name = "复古",
        warmth = 0.2f, saturation = -0.2f, contrast = 0.15f,
        vignette = 0.4f, grain = 0.3f
    )

    val CINEMATIC = FilterTemplate(
        id = "cinematic", name = "电影",
        contrast = 0.25f, saturation = -0.1f, warmth = 0.1f,
        vignette = 0.5f, tint = 0.05f
    )

    val BW = FilterTemplate(
        id = "bw", name = "黑白",
        saturation = -1.0f, contrast = 0.2f
    )

    /** 所有可展示的预设（不含"原片"，原片单独处理） */
    val ALL = listOf(WARM, COOL, FRESH, VINTAGE, CINEMATIC, BW)
}
