package com.ljp.vitalcam.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ljp.vitalcam.feature.camera.CameraScreen

/** 应用导航图，MVP 阶段仅包含相机路由 */
@Composable
fun VitalCamNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen()
        }
    }
}
