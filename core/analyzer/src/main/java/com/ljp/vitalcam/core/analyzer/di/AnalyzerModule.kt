package com.ljp.vitalcam.core.analyzer.di

import com.ljp.vitalcam.core.analyzer.RuleOfThirdsAnalyzer
import com.ljp.vitalcam.core.analyzer.SubjectDetectorStep
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
    abstract fun bindRuleOfThirdsAnalyzer(impl: RuleOfThirdsAnalyzer): AnalysisStep

    @Binds
    @IntoSet
    abstract fun bindSubjectDetectorStep(impl: SubjectDetectorStep): AnalysisStep
}
