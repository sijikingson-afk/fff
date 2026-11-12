package com.example.spatialwallpaper.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class GyroParallaxTracker(
    context: Context,
    private val onTiltChanged: (tiltX: Float, tiltY: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var smoothX = 0f
    private var smoothY = 0f
    private var basePitch: Float? = null
    private var baseRoll: Float? = null

    fun start() {
        basePitch = null
        baseRoll = null
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val pitch = orientationAngles[1]
        val roll = orientationAngles[2]

        if (basePitch == null || baseRoll == null) {
            basePitch = pitch
            baseRoll = roll
        }

        val rawX = ((roll - baseRoll!!) * 2.4f).coerceIn(-1f, 1f)
        val rawY = ((pitch - basePitch!!) * 2.4f).coerceIn(-1f, 1f)

        val alpha = 0.14f
        smoothX += alpha * (rawX - smoothX)
        smoothY += alpha * (rawY - smoothY)

        onTiltChanged(smoothX, smoothY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
