package com.ljp.vitalcam

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 应用入口，Hilt 依赖注入的根组件 */
@HiltAndroidApp
class VitalCamApp : Application()
