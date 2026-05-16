package com.ljp.vitalcam.core.pipeline

import com.ljp.vitalcam.core.common.AnalysisResult
import com.ljp.vitalcam.core.common.FrameData
import javax.inject.Inject
import javax.inject.Singleton

/** 分析管道执行器，按 order 顺序执行所有已启用的 AnalysisStep */
@Singleton
class AnalysisPipeline @Inject constructor(
    private val steps: Set<@JvmSuppressWildcards AnalysisStep>
) {
    /** 对一帧执行完整分析流程，返回最终结果 */
    suspend fun execute(frame: FrameData): AnalysisResult {
        var context = AnalysisContext()

        steps.filter { it.isEnabled() }
            .sortedBy { it.order }
            .forEach { step ->
                context = step.analyze(frame, context)
            }

        return context.toResult()
    }
}
