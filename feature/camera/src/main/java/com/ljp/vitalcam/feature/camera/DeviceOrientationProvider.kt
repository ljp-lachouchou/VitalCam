package com.ljp.vitalcam.feature.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** 通过旋转向量传感器提供设备 roll/pitch 角度 */
@Singleton
class DeviceOrientationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /** 绕视线轴旋转角度（度），0=水平，正值=顺时针倾斜 */
    @Volatile
    var rollDegrees: Float = 0f
        private set

    /** 俯仰角度（度），0=正对地平线，负值=俯拍（朝地面），正值=仰拍（朝天空） */
    @Volatile
    var pitchDegrees: Float = 0f
        private set

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles: [azimuth, pitch, roll] 弧度
        val pitchRad = orientationAngles[1]
        val rollRad = orientationAngles[2]

        pitchDegrees = Math.toDegrees(pitchRad.toDouble()).toFloat()
        // roll 取反使正值=顺时针倾斜（符合用户直觉）
        rollDegrees = -Math.toDegrees(rollRad.toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
