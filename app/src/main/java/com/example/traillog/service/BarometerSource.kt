package com.example.traillog.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 気圧センサー（IF-03）。搭載していない端末では [isAvailable] が false になり、値は常に null。
 * 値の揺らぎを抑えるため、指数移動平均で平滑化する。
 */
class BarometerSource @Inject constructor(
    @ApplicationContext context: Context,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean get() = sensor != null

    @Volatile
    var pressureHpa: Float? = null
        private set

    /** 標準大気を仮定した気圧高度。AltitudeFuser で GPS 高度と組み合わせて補正する。 */
    val altitudeM: Double?
        get() = pressureHpa?.let { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).toDouble() }

    fun start() {
        val s = sensor ?: return
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        val prev = pressureHpa
        pressureHpa = if (prev == null) value else prev + SMOOTHING * (value - prev)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SMOOTHING = 0.2f
        /** 省電力のため、センサーの値をまとめて受け取る（最大 5 秒遅れ）。 */
        const val BATCH_LATENCY_US = 5_000_000
    }
}
