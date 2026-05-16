package com.ljp.vitalcam.core.ml.di

import com.ljp.vitalcam.core.ml.MLRuntime
import com.ljp.vitalcam.core.ml.MediaPipeMLRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt 模块：绑定 ML 运行时实现 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MLModule {

    @Binds
    abstract fun bindMLRuntime(impl: MediaPipeMLRuntime): MLRuntime
}
