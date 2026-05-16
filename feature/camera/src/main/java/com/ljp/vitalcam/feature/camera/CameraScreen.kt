package com.ljp.vitalcam.feature.camera

import com.ljp.vitalcam.feature.camera.BuildConfig
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.overlay.RuleOfThirdsOverlay

/** 相机主界面：预览 + 网格叠加 + 引导提示 + 快门按钮 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel()
) {
    CameraPermission {
        CameraContent(
            viewModel = viewModel,
            modifier = modifier
        )
    }
}

/** 相机内容区域，在权限授权后展示 */
@Composable
private fun CameraContent(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()

    // 绑定 CameraX 到生命周期
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindCamera(context, lifecycleOwner)
    }

    val overlay = RuleOfThirdsOverlay()

    Box(modifier = modifier.fillMaxSize()) {
        // 相机预览层
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 三分法网格叠加层
        overlay.Render(
            modifier = Modifier.fillMaxSize(),
            analysisResult = analysisResult
        )

        // 主体检测边界框叠加层（仅 debug 构建显示）
        if (BuildConfig.DEBUG) {
            SubjectBoundsOverlay(
                subjects = analysisResult.subjects,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 顶部引导提示
        GuidanceOverlay(
            analysisResult = analysisResult,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // 底部快门按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 构图评分
            if (analysisResult.overallScore > 0) {
                Text(
                    text = "${analysisResult.overallScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            CaptureButton(
                onClick = { viewModel.capturePhoto(context) },
                enabled = uiState is CameraUiState.PreviewActive
            )
        }
    }
}

/** 顶部引导提示浮层 */
@Composable
private fun GuidanceOverlay(
    analysisResult: AnalysisResult,
    modifier: Modifier = Modifier
) {
    val topGuidance = analysisResult.guidances.firstOrNull() ?: return

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = topGuidance.message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** 绘制检测到的主体边界框和标签 */
@Composable
private fun SubjectBoundsOverlay(
    subjects: List<DetectedSubject>,
    modifier: Modifier = Modifier
) {
    if (subjects.isEmpty()) return

    val boxColor = Color(0xFF00E676)
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(160, 0, 230, 118)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        subjects.forEach { subject ->
            // 归一化坐标转像素坐标
            val left = (subject.centerX - subject.width / 2f) * w
            val top = (subject.centerY - subject.height / 2f) * h
            val right = (subject.centerX + subject.width / 2f) * w
            val bottom = (subject.centerY + subject.height / 2f) * h

            // 绘制边界框
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = strokeWidth)
            )

            // 绘制标签背景 + 文字
            val label = "${subject.label} ${(subject.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = 36f
            val labelPadding = 4f

            drawContext.canvas.nativeCanvas.apply {
                drawRect(
                    left,
                    top - textHeight - labelPadding * 2,
                    left + textWidth + labelPadding * 2,
                    top,
                    bgPaint
                )
                drawText(
                    label,
                    left + labelPadding,
                    top - labelPadding,
                    textPaint
                )
            }
        }
    }
}
