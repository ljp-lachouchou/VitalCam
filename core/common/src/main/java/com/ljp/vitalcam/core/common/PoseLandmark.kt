package com.ljp.vitalcam.core.common

/** 单个身体关键点（归一化坐标 0.0~1.0） */
data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

/** 一个人的完整姿势（33 个关键点） */
data class PersonPose(
    val landmarks: List<PoseLandmark>
) {
    companion object {
        const val NOSE = 0
        const val LEFT_EYE = 2
        const val RIGHT_EYE = 5
        const val LEFT_EAR = 7
        const val RIGHT_EAR = 8
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
    }

    fun get(index: Int): PoseLandmark = landmarks[index]
}
