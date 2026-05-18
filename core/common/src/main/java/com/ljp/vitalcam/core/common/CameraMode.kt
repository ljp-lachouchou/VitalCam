package com.ljp.vitalcam.core.common

/** 拍照模式枚举，每种模式定义允许的检测标签集合 */
enum class CameraMode(val label: String, val allowedLabels: Set<String>) {
    /** 自动模式：保留所有检测结果 */
    AUTO("自动", emptySet()),
    /** 人像模式：只关注人 */
    PORTRAIT("人像", setOf("person")),
    /** 风景模式：不依赖主体检测，靠水平线/对称等规则 */
    LANDSCAPE("风景", emptySet()),
    /** 美食模式：只保留食物相关目标 */
    FOOD(
        "美食",
        setOf(
            "bowl", "cup", "fork", "knife", "spoon",
            "bottle", "wine glass", "cake", "donut",
            "pizza", "hot dog", "sandwich"
        )
    ),
    /** 微距模式：保留面积最大的单个目标 */
    MACRO("微距", emptySet())
}
