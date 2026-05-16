# Analysis Pipeline 模式

## 什么是 Pipeline

Pipeline（管道）是一种将复杂处理拆分为多个独立步骤、按顺序串联执行的设计模式。每个步骤只关注自己的职责，通过共享上下文（Context）传递中间结果。

```
Input → [Step 1] → [Step 2] → [Step 3] → ... → [Step N] → Output
              └──────────── Context 逐步填充 ────────────┘
```

类比：工厂流水线。原材料（相机帧）经过多道工序（检测、分析、评估），最终产出成品（引导建议）。每道工序的工人只需要会自己那一步，不需要了解整条线。

## 为什么选择 Pipeline

| 需求 | Pipeline 如何解决 |
|---|---|
| 频繁增减分析能力 | 新增/移除一个 Step，不动其他代码 |
| 不同场景需要不同分析组合 | 运行时动态启用/禁用 Step |
| 各分析步骤独立演进 | 每个 Step 独立开发、测试、替换 |
| 步骤间有数据依赖 | Context 按顺序传递，后续 Step 可读取前序结果 |
| 性能调优 | 可单独对慢的 Step 做优化，或调整执行频率 |

## 核心接口

### AnalysisStep — 管道中的一道工序

```kotlin
interface AnalysisStep {

    /** 唯一标识，用于日志和配置 */
    val id: String

    /** 执行顺序，值越小越先执行 */
    val order: Int

    /** 是否启用（可通过配置或场景动态控制） */
    fun isEnabled(): Boolean

    /** 执行分析，读取 context 中的前序结果，写入自己的结果 */
    suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext
}
```

每个 Step 接收当前帧数据和上下文，返回更新后的上下文。职责单一，输入输出明确。

### AnalysisPipeline — 流水线调度器

```kotlin
class AnalysisPipeline(
    private val steps: List<AnalysisStep>
) {
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
```

Pipeline 本身不包含业务逻辑，只做三件事：过滤、排序、依次执行。

### AnalysisContext — 步骤间的共享上下文

```kotlin
data class AnalysisContext(
    val subjects: List<DetectedSubject> = emptyList(),
    val sceneType: SceneType? = null,
    val compositionScores: Map<String, CompositionScore> = emptyMap(),
    val horizontalTilt: Float = 0f,
    val guidances: List<Guidance> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
```

Context 是不可变数据类，每个 Step 通过 `copy()` 返回新实例。这保证了数据流向清晰，不会出现并发修改问题。

## 执行流程

```
CameraX ImageProxy
      │
      ▼
  FrameData (bitmap + rotation + timestamp)
      │
      ▼
  Pipeline.execute(frame)
      │
      ├─ Step 1: PreProcessor        order=100
      │   降采样、颜色转换、旋转校正
      │   写入: context.metadata["processedBitmap"]
      │
      ├─ Step 2: SubjectDetector     order=200
      │   ML 推理，检测人脸/物体/姿态
      │   读取: processedBitmap
      │   写入: context.subjects
      │
      ├─ Step 3: SceneClassifier     order=300
      │   场景分类（人像/风景/美食/微距）
      │   读取: processedBitmap, subjects
      │   写入: context.sceneType
      │
      ├─ Step 4: CompositionAnalyzer order=400
      │   构图规则评估（三分法/黄金比例/对称/水平线）
      │   读取: subjects, sceneType
      │   写入: context.compositionScores, context.horizontalTilt
      │
      └─ Step 5: GuidanceBuilder     order=900
          汇总所有结果，生成 1-2 条优先级最高的引导建议
          读取: subjects, sceneType, compositionScores, horizontalTilt
          写入: context.guidances
      │
      ▼
  AnalysisResult (overallScore + guidances + adjustments)
```

## Step 之间的依赖关系

Step 通过 `order` 字段保证执行顺序，通过 Context 的字段保证数据依赖：

```
PreProcessor (100)
      │ processedBitmap
      ▼
SubjectDetector (200) ◄── 依赖 processedBitmap
      │ subjects
      ▼
SceneClassifier (300) ◄── 依赖 processedBitmap, subjects
      │ sceneType
      ▼
CompositionAnalyzer (400) ◄── 依赖 subjects, sceneType
      │ compositionScores, horizontalTilt
      ▼
GuidanceBuilder (900) ◄── 依赖以上所有
```

**规则：后序 Step 可以读取前序 Step 写入的字段，但不能修改前序字段。** order 值建议留间隔（100, 200, 300...），方便未来在中间插入新 Step。

## 如何扩展

### 新增一个分析步骤

例如，增加"光线检测"能力：

```kotlin
class LightAnalyzer @Inject constructor() : AnalysisStep {

    override val id = "light_analyzer"
    override val order = 350  // 在 SceneClassifier 之后，CompositionAnalyzer 之前

    override fun isEnabled() = true

    override suspend fun analyze(frame: FrameData, context: AnalysisContext): AnalysisContext {
        val brightness = calculateBrightness(frame)
        val isBacklit = detectBacklight(frame, context.subjects)
        return context.copy(
            metadata = context.metadata + mapOf(
                "brightness" to brightness,
                "isBacklit" to isBacklit
            )
        )
    }
}
```

然后在 Hilt Module 中注册：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyzerModule {
    @Binds @IntoSet
    abstract fun bindLightAnalyzer(impl: LightAnalyzer): AnalysisStep
}
```

完成。不需要修改 Pipeline、其他 Step 或 ViewModel 的任何代码。

### 动态启用/禁用 Step

```kotlin
class SubjectDetector @Inject constructor(
    private val settingsRepository: SettingsRepository
) : AnalysisStep {

    override fun isEnabled() = settingsRepository.isSubjectDetectionEnabled()
    // ...
}
```

用户在设置中关闭主体检测 → 该 Step 自动跳过，Pipeline 继续运行其余步骤。

### 替换 Step 的实现

```kotlin
// 从 MediaPipe 迁移到自研模型，只需替换绑定
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyzerModule {
    // @Binds @IntoSet
    // abstract fun bindMediaPipeDetector(impl: MediaPipeSubjectDetector): AnalysisStep

    @Binds @IntoSet
    abstract fun bindCustomDetector(impl: CustomSubjectDetector): AnalysisStep
}
```

## 性能策略

Pipeline 运行在相机帧流上，性能是关键约束：

| 策略 | 做法 |
|---|---|
| **跳帧** | 每 3-5 帧执行一次 Pipeline，其余帧复用上次结果 |
| **降采样** | PreProcessor 将分辨率降到 480x270 再做分析 |
| **超时熔断** | 单个 Step 超过阈值（如 50ms）则跳过本次，用上次结果 |
| **后台线程** | Pipeline 在 `Dispatchers.Default` 协程中执行，不阻塞 UI 和相机 |
| **按需加载** | ML 模型仅在对应 Step 首次启用时加载 |

```kotlin
// 跳帧策略示例
class FrameThrottler(private val analyzeEveryN: Int = 3) {
    private var frameCount = 0
    private var lastResult: AnalysisResult? = null

    suspend fun maybeAnalyze(
        frame: FrameData,
        pipeline: AnalysisPipeline
    ): AnalysisResult {
        frameCount++
        return if (frameCount % analyzeEveryN == 0) {
            pipeline.execute(frame).also { lastResult = it }
        } else {
            lastResult ?: AnalysisResult.EMPTY
        }
    }
}
```

## 测试策略

Pipeline 模式天然适合单元测试，因为每个 Step 都是独立的纯函数（输入 → 输出）：

```kotlin
// 单独测试一个 Step
@Test
fun `CompositionAnalyzer scores rule of thirds correctly`() = runTest {
    val analyzer = CompositionAnalyzer()
    val context = AnalysisContext(
        subjects = listOf(DetectedSubject(centerX = 0.33f, centerY = 0.33f)),
        sceneType = SceneType.PORTRAIT
    )

    val result = analyzer.analyze(testFrame, context)

    assertTrue(result.compositionScores["rule_of_thirds"]!!.score > 80)
}

// 测试整条 Pipeline
@Test
fun `Pipeline produces guidance for off-center subject`() = runTest {
    val pipeline = AnalysisPipeline(listOf(
        FakePreProcessor(),
        FakeSubjectDetector(centerX = 0.1f),  // 主体偏左
        CompositionAnalyzer(),
        GuidanceBuilder()
    ))

    val result = pipeline.execute(testFrame)

    assertEquals(AdjustDirection.MOVE_RIGHT, result.guidances.first().direction)
}
```

## 与其他模式的对比

| 模式 | 适合场景 | VitalCam 为何选 Pipeline |
|---|---|---|
| **Pipeline** | 多步骤顺序处理，步骤间有数据依赖 | 分析链路是天然的顺序流，后步依赖前步结果 |
| **Chain of Responsibility** | 请求可能被某个节点终止 | 分析需要跑完所有步骤，不存在"某步处理完就结束" |
| **Observer/Event Bus** | 多个独立消费者，无执行顺序要求 | 分析步骤有严格顺序依赖 |
| **Strategy** | 同一接口多种互斥实现 | Pipeline 内的 Step 是协作关系，不是互斥关系 |

Pipeline 模式在 VitalCam 中的角色：**它是分析引擎的骨架，所有智能能力都以 Step 的形式插入这条管道。**
