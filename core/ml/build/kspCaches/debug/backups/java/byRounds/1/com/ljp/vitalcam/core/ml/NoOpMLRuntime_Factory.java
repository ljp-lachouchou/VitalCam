package com.ljp.vitalcam.core.ml;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class NoOpMLRuntime_Factory implements Factory<NoOpMLRuntime> {
  @Override
  public NoOpMLRuntime get() {
    return newInstance();
  }

  public static NoOpMLRuntime_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NoOpMLRuntime newInstance() {
    return new NoOpMLRuntime();
  }

  private static final class InstanceHolder {
    private static final NoOpMLRuntime_Factory INSTANCE = new NoOpMLRuntime_Factory();
  }
}
