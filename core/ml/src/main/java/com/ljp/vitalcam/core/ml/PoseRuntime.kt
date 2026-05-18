package com.ljp.vitalcam.core.ml

import android.graphics.Bitmap
import com.ljp.vitalcam.core.common.PersonPose

/** 姿势检测引擎抽象 */
interface PoseRuntime {

    val name: String

    fun isAvailable(): Boolean

    /** 执行姿势检测推理，返回检测到的人体姿势列表 */
    suspend fun detectPose(input: Bitmap): PoseDetectionResult

    fun close()
}

/** 姿势检测结果 */
data class PoseDetectionResult(
    val poses: List<PersonPose>
) {
    companion object {
        val EMPTY = PoseDetectionResult(emptyList())
    }
}
