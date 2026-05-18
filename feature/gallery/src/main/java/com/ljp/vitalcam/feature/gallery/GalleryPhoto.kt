package com.ljp.vitalcam.feature.gallery

import android.net.Uri

/** 相册中的单张照片 */
data class GalleryPhoto(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long
)
