package com.ljp.vitalcam.feature.camera

/** 相机界面 UI 状态 */
sealed interface CameraUiState {
    /** 初始状态 */
    data object Idle : CameraUiState

    /** 预览中 */
    data object PreviewActive : CameraUiState

    /** 拍照中 */
    data object Capturing : CameraUiState

    /** 拍照成功，准备跳转报告页 */
    data object CaptureSuccess : CameraUiState

    /** 错误状态 */
    data class Error(val message: String) : CameraUiState
}
