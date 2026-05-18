package com.ljp.vitalcam.core.analyzer.di

import com.ljp.vitalcam.core.analyzer.ColorAnalyzer
import com.ljp.vitalcam.core.analyzer.CompositionAnalyzer
import com.ljp.vitalcam.core.analyzer.GuidanceCoordinator
import com.ljp.vitalcam.core.analyzer.LightAnalyzer
import com.ljp.vitalcam.core.analyzer.PoseGuidanceAnalyzer
import com.ljp.vitalcam.core.analyzer.PoseLandmarkStep
import com.ljp.vitalcam.core.analyzer.SubjectDetectorStep
import com.ljp.vitalcam.core.analyzer.SubjectFramingAnalyzer
import com.ljp.vitalcam.core.pipeline.AnalysisStep
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Hilt 模块：将 AnalysisStep 实现注册到管道的 Set 中 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyzerModule {

    @Binds
    @IntoSet
    abstract fun bindCompositionAnalyzer(impl: CompositionAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindSubjectDetectorStep(impl: SubjectDetectorStep): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindSubjectFramingAnalyzer(impl: SubjectFramingAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindPoseLandmarkStep(impl: PoseLandmarkStep): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindPoseGuidanceAnalyzer(impl: PoseGuidanceAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindLightAnalyzer(impl: LightAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindColorAnalyzer(impl: ColorAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindGuidanceCoordinator(impl: GuidanceCoordinator): AnalysisStep
}
