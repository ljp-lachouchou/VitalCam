package com.ljp.vitalcam.core.filter.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt 模块：FilterEngine 和 FilterRecommender 通过 @Inject constructor 自动提供 */
@Module
@InstallIn(SingletonComponent::class)
object FilterModule
