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
import com.ljp.vitalcam.feature.gallery.GalleryScreen
import com.ljp.vitalcam.feature.gallery.GalleryViewModel
import com.ljp.vitalcam.feature.gallery.PhotoDetailScreen

/** 应用导航图：相机 + 相册 */
@Composable
fun VitalCamNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onNavigateToGallery = { navController.navigate("gallery_graph") }
            )
        }

        // 嵌套导航图：gallery_graph 内的路由共享同一个 GalleryViewModel
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
