package com.ljp.vitalcam.feature.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ljp.vitalcam.core.common.AnalysisResult

/** 拍照分析报告页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReportScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val captureResult by viewModel.captureResult.collectAsStateWithLifecycle()
    val result = captureResult ?: AnalysisResult.EMPTY

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 总分
            val scoreColor = scoreColor(result.overallScore)
            Text(
                text = "${result.overallScore}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                text = scoreLabel(result.overallScore),
                style = MaterialTheme.typography.titleMedium,
                color = scoreColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 各维度评分
            if (result.dimensionScores.isNotEmpty()) {
                Text(
                    text = "各维度评分",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                result.dimensionScores.forEach { (id, score) ->
                    val label = dimensionLabel(id) ?: return@forEach
                    DimensionScoreRow(
                        label = label,
                        score = score.score,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 改进建议
            val suggestions = result.guidances
                .filter { it.priority < 100 }
                .take(3)
            if (suggestions.isNotEmpty()) {
                Text(
                    text = "改进建议",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                suggestions.forEach { guidance ->
                    Text(
                        text = "• ${guidance.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 继续拍照按钮
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("继续拍照")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 维度评分行 */
@Composable
private fun DimensionScoreRow(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = scoreColor(score)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$score",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4CAF50)
    score >= 60 -> Color(0xFFFFEB3B)
    else -> Color(0xFFF44336)
}

private fun scoreLabel(score: Int): String = when {
    score >= 80 -> "很棒！"
    score >= 60 -> "不错，还能更好"
    score > 0 -> "有较大提升空间"
    else -> ""
}

/** 维度 ID 转中文名，返回 null 表示不显示 */
private fun dimensionLabel(id: String): String? = when (id) {
    "orientation" -> "姿态"
    "lighting" -> "光线"
    "color" -> "色彩"
    "composition" -> "构图"
    "subject_framing" -> "取景"
    "landscape_framing" -> "风景取景"
    "pose_guidance" -> "姿势"
    else -> null
}
