package com.ljp.vitalcam.core.pipeline;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.util.Set;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class AnalysisPipeline_Factory implements Factory<AnalysisPipeline> {
  private final Provider<Set<AnalysisStep>> stepsProvider;

  public AnalysisPipeline_Factory(Provider<Set<AnalysisStep>> stepsProvider) {
    this.stepsProvider = stepsProvider;
  }

  @Override
  public AnalysisPipeline get() {
    return newInstance(stepsProvider.get());
  }

  public static AnalysisPipeline_Factory create(Provider<Set<AnalysisStep>> stepsProvider) {
    return new AnalysisPipeline_Factory(stepsProvider);
  }

  public static AnalysisPipeline newInstance(Set<AnalysisStep> steps) {
    return new AnalysisPipeline(steps);
  }
}
