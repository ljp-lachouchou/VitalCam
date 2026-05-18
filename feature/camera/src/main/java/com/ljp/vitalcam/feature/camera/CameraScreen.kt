package com.ljp.vitalcam.feature.camera

import com.ljp.vitalcam.feature.camera.BuildConfig
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.overlay.RuleOfThirdsOverlay
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource

/** 相机主界面：预览 + 网格叠加 + 引导提示 + 快门按钮 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    onNavigateToGallery: () -> Unit = {},
    viewModel: CameraViewModel = hiltViewModel()
) {
    CameraPermission {
        CameraContent(
            viewModel = viewModel,
            onNavigateToGallery = onNavigateToGallery,
            modifier = modifier
        )
    }
}

/** 相机内容区域，在权限授权后展示 */
@Composable
private fun CameraContent(
    viewModel: CameraViewModel,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val cameraMode by viewModel.cameraMode.collectAsStateWithLifecycle()

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

        // 底部：模式选择器 + 评分 + 快门按钮 + 相册入口
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 拍照模式选择器
            CameraModeSelector(
                currentMode = cameraMode,
                onModeSelected = viewModel::setCameraMode
            )

            // 构图评分
            if (analysisResult.overallScore > 0) {
                Text(
                    text = "${analysisResult.overallScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            // 快门按钮 + 相册缩略图
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 左侧相册缩略图入口
                GalleryThumbnailButton(
                    onClick = onNavigateToGallery,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(24.dp))

                CaptureButton(
                    onClick = { viewModel.capturePhoto(context) },
                    enabled = uiState is CameraUiState.PreviewActive
                )

                // 右侧占位，保持快门居中
                Spacer(modifier = Modifier.width(72.dp))
            }
        }
    }
}

/** 顶部引导提示浮层，toast 式：新建议弹出显示 3 秒后淡出，新建议刷掉旧建议 */
@Composable
private fun GuidanceOverlay(
    analysisResult: AnalysisResult,
    modifier: Modifier = Modifier
) {
    val currentMessage = analysisResult.guidances.firstOrNull()?.message
    var displayedMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    // 仅在建议内容变化时触发：显示 → 停留 3 秒 → 淡出
    LaunchedEffect(currentMessage) {
        if (currentMessage != null) {
            displayedMessage = currentMessage
            visible = true
            delay(3000)
            visible = false
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
        exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { -it }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayedMessage ?: "",
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
}

/** 拍照模式水平选择器 */
@Composable
private fun CameraModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(CameraMode.entries.toList()) { mode ->
            FilterChip(
                selected = mode == currentMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.White.copy(alpha = 0.3f),
                    selectedLabelColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.7f)
                )
            )
        }
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

/** 相册缩略图入口按钮：显示最近一张照片或占位图标 */
@Composable
private fun GalleryThumbnailButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 查询最近一张 VitalCam 照片的 URI
    val latestPhotoUri = remember {
        queryLatestVitalCamPhoto(context)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (latestPhotoUri != null) {
            AsyncImage(
                model = latestPhotoUri,
                contentDescription = "打开相册",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                contentDescription = "打开相册",
                tint = Color.White
            )
        }
    }
}

/** 查询 Pictures/VitalCam/ 下最新一张照片的 URI */
private fun queryLatestVitalCamPhoto(context: android.content.Context): android.net.Uri? {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)

    val selection: String
    val selectionArgs: Array<String>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        selectionArgs = arrayOf("Pictures/VitalCam%")
    } else {
        @Suppress("DEPRECATION")
        selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        selectionArgs = arrayOf("%/Pictures/VitalCam/%")
    }

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        collection, projection, selection, selectionArgs, sortOrder
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            return ContentUris.withAppendedId(collection, id)
        }
    }
    return null
}
