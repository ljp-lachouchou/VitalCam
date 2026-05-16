package com.ljp.vitalcam.core.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/** MVP 阶段的空实现，始终返回空结果 */
@Singleton
class NoOpMLRuntime @Inject constructor() : MLRuntime {

    override val name: String = "no-op"

    override fun isAvailable(): Boolean = true

    override suspend fun detectObjects(input: Bitmap): ObjectDetectionResult {
        return ObjectDetectionResult.EMPTY
    }

    override fun close() {}
}
