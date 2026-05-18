package com.ljp.vitalcam.feature.camera

import com.ljp.vitalcam.feature.camera.BuildConfig
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.CameraMode
import com.ljp.vitalcam.core.common.AdjustDirection
import com.ljp.vitalcam.core.common.DetectedSubject
import com.ljp.vitalcam.core.overlay.RuleOfThirdsOverlay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onNavigateToReport: () -> Unit = {},
    viewModel: CameraViewModel = hiltViewModel()
) {
    CameraPermission {
        CameraContent(
            viewModel = viewModel,
            onNavigateToGallery = onNavigateToGallery,
            onNavigateToReport = onNavigateToReport,
            modifier = modifier
        )
    }
}

/** 相机内容区域，在权限授权后展示 */
@Composable
private fun CameraContent(
    viewModel: CameraViewModel,
    onNavigateToGallery: () -> Unit,
    onNavigateToReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val cameraMode by viewModel.cameraMode.collectAsStateWithLifecycle()

    // 绑定 CameraX 到生命周期
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindCamera(context, lifecycleOwner)
    }

    // 拍照闪光
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is CameraUiState.Capturing) {
            showFlash = true
            delay(150)
            showFlash = false
        }
        if (uiState is CameraUiState.CaptureSuccess) {
            viewModel.onCaptureResultConsumed()
            onNavigateToReport()
        }
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

        // 全屏方向引导蒙层
        GuidanceOverlay(
            analysisResult = analysisResult,
            modifier = Modifier.fillMaxSize()
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

            // 构图评分（颜色随分数变化）
            if (analysisResult.overallScore > 0) {
                val scoreColor = when {
                    analysisResult.overallScore >= 80 -> Color(0xFF4CAF50)
                    analysisResult.overallScore >= 60 -> Color(0xFFFFEB3B)
                    else -> Color(0xFFF44336)
                }
                Text(
                    text = "${analysisResult.overallScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = scoreColor
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.capturePhoto(context)
                    },
                    enabled = uiState is CameraUiState.PreviewActive
                )

                // 右侧占位，保持快门居中
                Spacer(modifier = Modifier.width(72.dp))
            }
        }

        // 拍照闪光叠加层（最顶层）
        AnimatedVisibility(
            visible = showFlash,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

/** 全屏边缘方向引导蒙层：在对应边缘显示渐变 + 浮动箭头动画 */
@Composable
private fun GuidanceOverlay(
    analysisResult: AnalysisResult,
    modifier: Modifier = Modifier
) {
    val topGuidance = analysisResult.guidances.firstOrNull()
    val currentDirection = topGuidance?.direction
    val currentMessage = topGuidance?.message
    var displayedDirection by remember { mutableStateOf<AdjustDirection?>(null) }
    var displayedMessage by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    // 引导内容变化时：显示 → 停留 3 秒 → 淡出
    LaunchedEffect(currentDirection, currentMessage) {
        if (currentDirection != null && currentMessage != null) {
            displayedDirection = currentDirection
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
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(400))
    ) {
        val dir = displayedDirection ?: return@AnimatedVisibility
        when (dir) {
            AdjustDirection.MOVE_LEFT,
            AdjustDirection.MOVE_RIGHT,
            AdjustDirection.MOVE_UP,
            AdjustDirection.MOVE_DOWN -> EdgeArrowOverlay(direction = dir, message = displayedMessage)

            AdjustDirection.ZOOM_IN -> ZoomPulseOverlay(zoomIn = true, message = displayedMessage)
            AdjustDirection.ZOOM_OUT -> ZoomPulseOverlay(zoomIn = false, message = displayedMessage)

            AdjustDirection.TILT_CW -> TiltOverlay(clockwise = true, message = displayedMessage)
            AdjustDirection.TILT_CCW -> TiltOverlay(clockwise = false, message = displayedMessage)

            AdjustDirection.NONE -> TextOnlyOverlay(message = displayedMessage)
        }
    }
}

/** 边缘渐变蒙层 + 浮动箭头 + 文字提示 */
@Composable
private fun EdgeArrowOverlay(direction: AdjustDirection, message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow-float")
    // 箭头浮动偏移量（dp）
    val floatOffsetDp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow-offset"
    )

    val isHorizontal = direction == AdjustDirection.MOVE_LEFT || direction == AdjustDirection.MOVE_RIGHT

    Box(modifier = Modifier.fillMaxSize()) {
        if (isHorizontal) {
            val isLeft = direction == AdjustDirection.MOVE_LEFT
            val gradientColors = if (isLeft) {
                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
            } else {
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
            }
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .background(Brush.horizontalGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                // 箭头 + 文字纵向排列
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val xOffset = if (isLeft) -floatOffsetDp else floatOffsetDp
                    Canvas(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(x = xOffset.dp)
                    ) {
                        val arrowPath = Path().apply {
                            if (isLeft) {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height / 2f)
                                lineTo(size.width, size.height)
                            } else {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height / 2f)
                                lineTo(0f, size.height)
                            }
                            close()
                        }
                        drawPath(arrowPath, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        } else {
            val isUp = direction == AdjustDirection.MOVE_UP
            val gradientColors = if (isUp) {
                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
            } else {
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
            }
            Box(
                modifier = Modifier
                    .align(if (isUp) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.2f)
                    .background(Brush.verticalGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                // 箭头 + 文字纵向排列
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isUp) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    val yOffset = if (isUp) -floatOffsetDp else floatOffsetDp
                    Canvas(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = yOffset.dp)
                    ) {
                        val arrowPath = Path().apply {
                            if (isUp) {
                                moveTo(0f, size.height)
                                lineTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                            } else {
                                moveTo(0f, 0f)
                                lineTo(size.width / 2f, size.height)
                                lineTo(size.width, 0f)
                            }
                            close()
                        }
                        drawPath(arrowPath, color = Color.White.copy(alpha = 0.85f))
                    }
                    if (isUp) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** 缩放脉冲蒙层：四边渐变 + 中心圆环脉冲 + 文字 */
@Composable
private fun ZoomPulseOverlay(zoomIn: Boolean, message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "zoom-pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = if (zoomIn) 0.3f else 0.8f,
        targetValue = if (zoomIn) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "zoom-radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "zoom-alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 四边渐变 + 圆环
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgeWidth = size.width * 0.15f
            val edgeHeight = size.height * 0.1f
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent),
                    startX = 0f, endX = edgeWidth
                ),
                topLeft = Offset.Zero, size = Size(edgeWidth, size.height)
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                    startX = size.width - edgeWidth, endX = size.width
                ),
                topLeft = Offset(size.width - edgeWidth, 0f), size = Size(edgeWidth, size.height)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent),
                    startY = 0f, endY = edgeHeight
                ),
                topLeft = Offset.Zero, size = Size(size.width, edgeHeight)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                    startY = size.height - edgeHeight, endY = size.height
                ),
                topLeft = Offset(0f, size.height - edgeHeight), size = Size(size.width, edgeHeight)
            )

            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.25f
            val radius = maxRadius * pulseProgress
            drawCircle(
                color = Color.White.copy(alpha = pulseAlpha),
                radius = radius, center = center, style = Stroke(width = 4f)
            )
        }

        // 圆环下方文字
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 倾斜引导蒙层：中心旋转弧形箭头 + 文字 */
@Composable
private fun TiltOverlay(clockwise: Boolean, message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tilt-rotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = if (clockwise) -15f else 15f,
        targetValue = if (clockwise) 15f else -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt-angle"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcRadius = size.minDimension * 0.2f

            rotate(rotationAngle, pivot = center) {
                drawArc(
                    color = Color.White.copy(alpha = 0.7f),
                    startAngle = -60f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = 4f)
                )

                val arrowSize = arcRadius * 0.25f
                val arrowPath = Path()
                if (clockwise) {
                    val tipX = center.x + arcRadius * kotlin.math.cos(Math.toRadians(60.0)).toFloat()
                    val tipY = center.y - arcRadius * kotlin.math.sin(Math.toRadians(60.0)).toFloat()
                    arrowPath.moveTo(tipX, tipY)
                    arrowPath.lineTo(tipX + arrowSize * 0.5f, tipY - arrowSize)
                    arrowPath.lineTo(tipX - arrowSize * 0.5f, tipY - arrowSize * 0.3f)
                    arrowPath.close()
                } else {
                    val tipX = center.x - arcRadius * kotlin.math.cos(Math.toRadians(60.0)).toFloat()
                    val tipY = center.y - arcRadius * kotlin.math.sin(Math.toRadians(60.0)).toFloat()
                    arrowPath.moveTo(tipX, tipY)
                    arrowPath.lineTo(tipX - arrowSize * 0.5f, tipY - arrowSize)
                    arrowPath.lineTo(tipX + arrowSize * 0.5f, tipY - arrowSize * 0.3f)
                    arrowPath.close()
                }
                drawPath(arrowPath, color = Color.White.copy(alpha = 0.85f))
            }
        }

        // 弧线下方文字
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 纯文字引导蒙层：用于 pitch 角度提示等无方向的引导 */
@Composable
private fun TextOnlyOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 48.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
