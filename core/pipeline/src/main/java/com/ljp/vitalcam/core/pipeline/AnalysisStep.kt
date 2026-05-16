package com.ljp.vitalcam.core.pipeline

import com.ljp.vitalcam.core.common.FrameData

/** 分析管道中的单个步骤，每个步骤负责一种分析能力 */
interface AnalysisStep {

    /** 唯一标识，用于日志和配置 */
    val id: String

    /** 执行顺序，值越小越先执行，建议按 100 递增留间隔 */
    val order: Int

    /** 是否启用，可通过配置或场景动态控制 */
    fun isEnabled(): Boolean

    /** 执行分析：读取 context 中的前序结果，写入自己的结果并返回新 context */
    suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext
}
