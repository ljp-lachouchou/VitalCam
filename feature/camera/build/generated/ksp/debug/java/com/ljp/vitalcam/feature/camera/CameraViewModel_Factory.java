package com.ljp.vitalcam.feature.camera;

import com.ljp.vitalcam.core.pipeline.AnalysisPipeline;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class CameraViewModel_Factory implements Factory<CameraViewModel> {
  private final Provider<AnalysisPipeline> analysisPipelineProvider;

  public CameraViewModel_Factory(Provider<AnalysisPipeline> analysisPipelineProvider) {
    this.analysisPipelineProvider = analysisPipelineProvider;
  }

  @Override
  public CameraViewModel get() {
    return newInstance(analysisPipelineProvider.get());
  }

  public static CameraViewModel_Factory create(
      Provider<AnalysisPipeline> analysisPipelineProvider) {
    return new CameraViewModel_Factory(analysisPipelineProvider);
  }

  public static CameraViewModel newInstance(AnalysisPipeline analysisPipeline) {
    return new CameraViewModel(analysisPipeline);
  }
}
