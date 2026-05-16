package com.ljp.vitalcam.core.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ljp.vitalcam.core.common.AnalysisResult

/** 三分法网格叠加层，在预览上绘制 2 横 2 纵参考线 */
class RuleOfThirdsOverlay : OverlayRenderer {

    override val id: String = "rule_of_thirds_grid"

    @Composable
    override fun Render(modifier: Modifier, analysisResult: AnalysisResult) {
        val lineColor = Color.White.copy(alpha = 0.5f)
        val strokeWidth = 1.dp

        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = strokeWidth.toPx()

            // 绘制 2 条垂直三分线
            for (i in 1..2) {
                val x = w * i / 3f
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = stroke
                )
            }

            // 绘制 2 条水平三分线
            for (i in 1..2) {
                val y = h * i / 3f
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = stroke
                )
            }

            // 在 4 个交叉点绘制小圆点，帮助对齐主体
            for (col in 1..2) {
                for (row in 1..2) {
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = Offset(w * col / 3f, h * row / 3f)
                    )
                }
            }
        }
    }
}
