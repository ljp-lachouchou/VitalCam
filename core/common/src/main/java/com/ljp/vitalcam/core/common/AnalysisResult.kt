package com.ljp.vitalcam.core.common

/** 分析管道的最终输出结果 */
data class AnalysisResult(
    /** 整体构图评分 0~100 */
    val overallScore: Int,
    /** 引导建议列表（按优先级排序） */
    val guidances: List<Guidance>,
    /** 检测到的主体列表 */
    val subjects: List<DetectedSubject>,
    /** 扩展元数据 */
    val metadata: Map<String, Any>
) {
    companion object {
        /** 空结果，用于初始状态或跳帧时复用 */
        val EMPTY = AnalysisResult(
            overallScore = 0,
            guidances = emptyList(),
            subjects = emptyList(),
            metadata = emptyMap()
        )
    }
}
