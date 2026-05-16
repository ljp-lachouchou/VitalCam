package com.ljp.vitalcam.core.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ljp.vitalcam.core.common.AnalysisResult

/** 相机预览叠加层渲染器接口，不同的渲染样式实现此接口 */
interface OverlayRenderer {

    /** 渲染器唯一标识 */
    val id: String

    /** 在相机预览上层绘制叠加内容 */
    @Composable
    fun Render(modifier: Modifier, analysisResult: AnalysisResult)
}
