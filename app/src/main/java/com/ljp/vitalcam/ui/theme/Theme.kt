package com.ljp.vitalcam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** 暗色主题，适合相机应用 */
private val DarkColorScheme = darkColorScheme()

@Composable
fun VitalCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
