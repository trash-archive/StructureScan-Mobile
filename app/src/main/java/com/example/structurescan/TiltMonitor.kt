package com.example.structurescan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.sqrt

/**
 * Data class to store tilt measurement from device sensors
 */
@Parcelize
data class TiltMeasurement(
    val pitch: Float,           // Forward/backward tilt (degrees)
    val roll: Float,            // Left/right tilt (degrees)
    val tiltMagnitude: Float,   // Overall tilt from level (degrees)
    val timestamp: Long
) : Parcelable

/**
 * Quality assessment thresholds based on photogrammetry standards (ASTM E2270)
 */
object TiltThresholds {
    const val EXCELLENT_THRESHOLD = 3.0f    // ≤3° = professional grade
    const val ACCEPTABLE_THRESHOLD = 5.0f   // ≤5° = acceptable for assessment
    const val WARNING_THRESHOLD = 8.0f      // ≤8° = marginal, warn user
    const val REJECT_THRESHOLD = 10.0f      // >10° = too much distortion

    // Confidence penalties (based on crack measurement error studies)
    const val EXCELLENT_MULTIPLIER = 1.00f  // No penalty
    const val ACCEPTABLE_MULTIPLIER = 0.95f // ~5% confidence reduction
    const val WARNING_MULTIPLIER = 0.85f    // ~15% confidence reduction
    const val REJECT_MULTIPLIER = 0.0f      // Image rejected
}

enum class ImageQuality {
    EXCELLENT,   // Tilt ≤3°
    ACCEPTABLE,  // Tilt 3-5°
    MARGINAL,    // Tilt 5-10°
    REJECTED     // Tilt >10°
}

/**
 * Assess image quality based on tilt measurement
 */
fun assessImageQuality(tilt: TiltMeasurement): ImageQuality {
    return when {
        tilt.tiltMagnitude <= TiltThresholds.EXCELLENT_THRESHOLD -> ImageQuality.EXCELLENT
        tilt.tiltMagnitude <= TiltThresholds.ACCEPTABLE_THRESHOLD -> ImageQuality.ACCEPTABLE
        tilt.tiltMagnitude <= TiltThresholds.REJECT_THRESHOLD -> ImageQuality.MARGINAL
        else -> ImageQuality.REJECTED
    }
}

/**
 * Monitor device tilt using Android's Rotation Vector sensor
 * This sensor fuses accelerometer, gyroscope, and magnetometer data automatically
 */
class TiltMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Use Rotation Vector - it's already fused by Android (best practice for AR/photogrammetry)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var latestRotationVector: FloatArray? = null

    private var isListening = false

    /**
     * Start listening to sensor updates
     */
    fun startListening() {
        if (!isListening && rotationSensor != null) {
            sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI // Update ~60Hz, good balance of accuracy and battery
            )
            isListening = true
        }
    }

    /**
     * Stop listening to sensor updates (call in onPause/onDestroy)
     */
    fun stopListening() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    /**
     * Get current tilt measurement
     */
    fun getCurrentTilt(): TiltMeasurement? {
        val rotationVector = latestRotationVector ?: return null

        // Android does the sensor fusion for us
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert radians to degrees
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Combined tilt magnitude (Euclidean distance from level)
        val tiltMagnitude = sqrt((pitch * pitch + roll * roll).toDouble()).toFloat()

        return TiltMeasurement(
            pitch = pitch,
            roll = roll,
            tiltMagnitude = tiltMagnitude,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            latestRotationVector = event.values.clone()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for rotation vector sensor
    }

    /**
     * Check if device has rotation vector sensor
     */
    fun isAvailable(): Boolean {
        return rotationSensor != null
    }
}
