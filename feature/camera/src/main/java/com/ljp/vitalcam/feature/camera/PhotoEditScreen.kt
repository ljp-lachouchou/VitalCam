package com.ljp.vitalcam.feature.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.filter.FilterEngine
import com.ljp.vitalcam.core.filter.FilterPresets
import com.ljp.vitalcam.core.filter.FilterRecommender
import com.ljp.vitalcam.core.filter.FilterTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 照片编辑页：AI 自动滤镜 + 推荐模板列表 + 保存 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onViewReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoUri by viewModel.capturedPhotoUri.collectAsStateWithLifecycle()
    val captureResult by viewModel.captureResult.collectAsStateWithLifecycle()
    val rollDegrees by viewModel.captureRollDegrees.collectAsStateWithLifecycle()

    val filterEngine = remember { FilterEngine() }
    val filterRecommender = remember { FilterRecommender() }

    // 预览状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var templates by remember { mutableStateOf<List<FilterTemplate>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf<FilterTemplate?>(null) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    // 加载照片并生成推荐
    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.Default) {
                // 加载预览尺寸 bitmap
                val source = loadScaledBitmap(context, uri.toString(), 1080)
                    ?: return@withContext
                sourceBitmap = source

                // 生成推荐模板列表
                val result = captureResult ?: AnalysisResult.EMPTY
                val recommended = filterRecommender.recommend(result, rollDegrees)
                templates = recommended

                // 自动应用 AI 推荐（第一个）
                val aiTemplate = recommended.first()
                selectedTemplate = aiTemplate
                previewBitmap = filterEngine.apply(source, aiTemplate)

                // 生成缩略图
                val thumbSize = 120
                val thumbSource = Bitmap.createScaledBitmap(source, thumbSize, thumbSize, true)
                val thumbMap = mutableMapOf<String, Bitmap>()
                thumbMap["original"] = thumbSource
                recommended.forEach { template ->
                    thumbMap[template.id] = filterEngine.apply(thumbSource, template)
                }
                thumbnails = thumbMap
            }
        } catch (_: Exception) {
            // 降级：直接用原始解码显示，避免界面永久卡在加载状态
            val path = uri.path
            if (path != null) {
                val fallback = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeFile(path)
                }
                if (fallback != null) {
                    sourceBitmap = fallback
                    previewBitmap = fallback
                }
            }
        }
    }

    // 返回时清理临时文件（未保存则删除）
    val handleBack = {
        if (!saveSuccess) viewModel.cleanupTempPhoto()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("照片编辑") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_revert),
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 照片预览区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            // 滤镜模板列表
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 原片选项
                item {
                    FilterThumbnail(
                        name = "原片",
                        thumbnail = thumbnails["original"],
                        isSelected = selectedTemplate == null,
                        isAi = false,
                        onClick = {
                            selectedTemplate = null
                            scope.launch(Dispatchers.Default) {
                                sourceBitmap?.let { previewBitmap = it }
                            }
                        }
                    )
                }
                // 推荐模板
                items(templates, key = { it.id }) { template ->
                    FilterThumbnail(
                        name = template.name,
                        thumbnail = thumbnails[template.id],
                        isSelected = selectedTemplate?.id == template.id,
                        isAi = template.id == "ai_auto",
                        onClick = {
                            selectedTemplate = template
                            scope.launch(Dispatchers.Default) {
                                sourceBitmap?.let { src ->
                                    previewBitmap = filterEngine.apply(src, template)
                                }
                            }
                        }
                    )
                }
            }

            // 底部按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onViewReport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看报告")
                }
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        scope.launch {
                            val uri = photoUri
                            if (uri != null) {
                                val template = selectedTemplate
                                if (template != null) {
                                    // 有滤镜：处理后保存
                                    saveFilteredPhoto(context, uri.toString(), template, filterEngine)
                                } else {
                                    // 原片：直接保存临时文件内容到相册
                                    saveOriginalPhoto(context, uri.toString())
                                }
                                viewModel.cleanupTempPhoto()
                            }
                            isSaving = false
                            saveSuccess = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && !saveSuccess
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else if (saveSuccess) {
                        Text("已保存 ✓")
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}

/** 滤镜缩略图项 */
@Composable
private fun FilterThumbnail(
    name: String,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    isAi: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    ) else Modifier
                )
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // AI 标记
            if (isAi) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(bottomEnd = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** 加载缩放后的 Bitmap，支持 file:// 临时文件 URI，并应用 EXIF 旋转 */
private fun loadScaledBitmap(context: Context, uriString: String, maxWidth: Int): Bitmap? {
    val uri = android.net.Uri.parse(uriString)
    val filePath = if (uri.scheme == "file") uri.path else null

    fun openStream(): java.io.InputStream? = try {
        if (filePath != null) java.io.FileInputStream(filePath)
        else context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    }

    // 先读取尺寸（inJustDecodeBounds=true 时 decodeStream 返回 null 是正常行为，不能用结果判空）
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = openStream() ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, options) }

    // 计算 inSampleSize
    val sampleSize = (options.outWidth / maxWidth).coerceAtLeast(1)

    // 重新读取缩放图
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

    // 应用 EXIF 旋转（仅 file:// 路径可读取 EXIF）
    return if (filePath != null) rotateBitmapByExif(decoded, filePath) else decoded
}

/** 读取临时文件 Bitmap，支持 file:// URI，并应用 EXIF 旋转 */
private fun openTempBitmap(uriString: String): Bitmap? {
    val uri = android.net.Uri.parse(uriString)
    val path = uri.path ?: return null
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    return rotateBitmapByExif(bitmap, path)
}

/** 从 JPEG 文件读取 EXIF 旋转角度（度），读取失败时返回 0 */
private fun exifRotationDegrees(path: String): Float = try {
    val exif = android.media.ExifInterface(path)
    when (exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL
    )) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
} catch (_: Exception) {
    0f
}

/** 按 EXIF 旋转角度修正 Bitmap 方向，0 度直接返回原图 */
private fun rotateBitmapByExif(bitmap: Bitmap, path: String): Bitmap {
    val degrees = exifRotationDegrees(path)
    if (degrees == 0f) return bitmap
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** 构建 MediaStore ContentValues */
private fun buildPhotoContentValues(): ContentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, "VitalCam_${System.currentTimeMillis()}")
    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VitalCam")
    }
}

/** 对全分辨率照片应用滤镜并保存到 MediaStore */
private suspend fun saveFilteredPhoto(
    context: Context,
    uriString: String,
    template: FilterTemplate,
    engine: FilterEngine
) {
    withContext(Dispatchers.Default) {
        val fullBitmap = openTempBitmap(uriString) ?: return@withContext
        val filtered = engine.apply(fullBitmap, template)

        val saveUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, buildPhotoContentValues()
        ) ?: return@withContext
        context.contentResolver.openOutputStream(saveUri)?.use { out ->
            filtered.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
    }
}

/** 原片直接从临时文件写入 MediaStore，不做任何处理 */
private suspend fun saveOriginalPhoto(context: Context, uriString: String) {
    withContext(Dispatchers.Default) {
        val srcFile = java.io.File(android.net.Uri.parse(uriString).path ?: return@withContext)
        if (!srcFile.exists()) return@withContext

        val saveUri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, buildPhotoContentValues()
        ) ?: return@withContext
        context.contentResolver.openOutputStream(saveUri)?.use { out ->
            srcFile.inputStream().use { it.copyTo(out) }
        }
    }
}
