package com.ljp.vitalcam.core.analyzer;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class RuleOfThirdsAnalyzer_Factory implements Factory<RuleOfThirdsAnalyzer> {
  @Override
  public RuleOfThirdsAnalyzer get() {
    return newInstance();
  }

  public static RuleOfThirdsAnalyzer_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RuleOfThirdsAnalyzer newInstance() {
    return new RuleOfThirdsAnalyzer();
  }

  private static final class InstanceHolder {
    private static final RuleOfThirdsAnalyzer_Factory INSTANCE = new RuleOfThirdsAnalyzer_Factory();
  }
}
