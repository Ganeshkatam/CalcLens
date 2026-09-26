package com.calclens.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.max

/**
 * High-frequency IMU motion predictor that tracks device rotation using the gyroscope.
 * Updates at 200–500 Hz to predict spatial camera displacement between vision frames,
 * achieving sub-10 ms motion-to-overlay latency.
 */
class ImuMotionPredictor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var lastTimestampNs: Long = 0L

    // Accumulated normalized translation delta (in [0, 1] screen space)
    @Volatile
    var accumulatedDeltaX: Float = 0f
        private set

    @Volatile
    var accumulatedDeltaY: Float = 0f
        private set

    // Camera field of view scaling factor mapping radians of rotation to normalized image shift
    // For typical wide-angle phone cameras (~65-70 degree FOV): 1 radian ≈ 0.85 screen width
    var fovFactor: Float = 0.82f

    fun start() {
        gyroscope?.let { sensor ->
            try {
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            } catch (e: Exception) {
                try {
                    sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                } catch (fallbackEx: Exception) {
                    android.util.Log.w("CalcLens", "Failed to register gyroscope: ${fallbackEx.message}")
                }
            }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        lastTimestampNs = 0L
    }

    /**
     * Consumes and resets the accumulated motion deltas since last query.
     * Returns Pair(deltaX, deltaY) in normalized [0, 1] coordinates.
     */
    @Synchronized
    fun consumeDelta(): Pair<Float, Float> {
        val dx = accumulatedDeltaX
        val dy = accumulatedDeltaY
        accumulatedDeltaX = 0f
        accumulatedDeltaY = 0f
        return Pair(dx, dy)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val currentTimestampNs = event.timestamp
        if (lastTimestampNs != 0L) {
            val dt = (currentTimestampNs - lastTimestampNs) * 1e-9f // Convert ns to seconds
            if (dt in 0.0001f..0.100f) {
                // Gyroscope values in rad/s:
                // event.values[0] = pitch rate (rotation around X axis -> moves camera up/down in Y)
                // event.values[1] = yaw rate (rotation around Y axis -> moves camera left/right in X)
                val pitchRate = event.values[0]
                val yawRate = event.values[1]

                // Coordinate mapping for portrait camera:
                // Turning phone right (positive yaw) causes physical objects in viewfinder to move LEFT (-X)
                // Tilting phone down (negative pitch) causes physical objects in viewfinder to move UP (-Y)
                val shiftX = -yawRate * fovFactor * dt
                val shiftY = pitchRate * fovFactor * dt

                synchronized(this) {
                    accumulatedDeltaX += shiftX
                    accumulatedDeltaY += shiftY
                }
            }
        }
        lastTimestampNs = currentTimestampNs
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
