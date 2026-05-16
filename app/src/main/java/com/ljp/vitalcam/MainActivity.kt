package com.ljp.vitalcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ljp.vitalcam.navigation.VitalCamNavHost
import com.ljp.vitalcam.ui.theme.VitalCamTheme
import dagger.hilt.android.AndroidEntryPoint

/** 应用主 Activity，使用 Compose 渲染 UI */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitalCamTheme {
                VitalCamNavHost()
            }
        }
    }
}
