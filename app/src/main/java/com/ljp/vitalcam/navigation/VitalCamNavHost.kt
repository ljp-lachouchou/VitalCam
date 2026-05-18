package com.ljp.vitalcam.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.ljp.vitalcam.feature.camera.CameraScreen
import com.ljp.vitalcam.feature.camera.CameraViewModel
import com.ljp.vitalcam.feature.camera.PhotoEditScreen
import com.ljp.vitalcam.feature.camera.PhotoReportScreen
import com.ljp.vitalcam.feature.gallery.GalleryScreen
import com.ljp.vitalcam.feature.gallery.GalleryViewModel
import com.ljp.vitalcam.feature.gallery.PhotoDetailScreen

/** 应用导航图：相机(+报告) + 相册 */
@Composable
fun VitalCamNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = "camera_graph") {

        // 相机嵌套导航图：camera 和 report 共享 CameraViewModel
        navigation(startDestination = "camera", route = "camera_graph") {
            composable("camera") { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry("camera_graph") }
                val cameraViewModel: CameraViewModel = hiltViewModel(graphEntry)
                CameraScreen(
                    onNavigateToGallery = { navController.navigate("gallery_graph") },
                    onNavigateToReport = { navController.navigate("editor") },
                    viewModel = cameraViewModel
                )
            }
            composable("editor") { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry("camera_graph") }
                val cameraViewModel: CameraViewModel = hiltViewModel(graphEntry)
                PhotoEditScreen(
                    viewModel = cameraViewModel,
                    onBack = { navController.popBackStack() },
                    onViewReport = { navController.navigate("report") }
                )
            }
            composable("report") { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry("camera_graph") }
                val cameraViewModel: CameraViewModel = hiltViewModel(graphEntry)
                PhotoReportScreen(
                    viewModel = cameraViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // 相册嵌套导航图
        navigation(startDestination = "gallery", route = "gallery_graph") {
            composable("gallery") { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry("gallery_graph") }
                val galleryViewModel: GalleryViewModel = hiltViewModel(graphEntry)
                GalleryScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoClick = { uri ->
                        navController.navigate("gallery/detail/${Uri.encode(uri)}")
                    },
                    viewModel = galleryViewModel
                )
            }
            composable("gallery/detail/{uri}") { entry ->
                val encodedUri = entry.arguments?.getString("uri") ?: ""
                val photoUri = Uri.decode(encodedUri)
                val graphEntry = remember(entry) { navController.getBackStackEntry("gallery_graph") }
                val galleryViewModel: GalleryViewModel = hiltViewModel(graphEntry)
                PhotoDetailScreen(
                    photoUri = photoUri,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    deletePhoto = { uri -> galleryViewModel.deletePhoto(uri) }
                )
            }
        }
    }
}
