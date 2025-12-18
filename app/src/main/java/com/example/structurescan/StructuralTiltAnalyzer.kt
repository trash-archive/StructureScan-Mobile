package com.example.structurescan

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Enhanced structural tilt analyzer with:
 * - Camera tilt compensation
 * - Multi-frame analysis
 * - Outlier removal
 * - Improved confidence scoring
 */
class StructuralTiltAnalyzer {

    companion object {
        private var isOpenCVLoaded = false

        init {
            try {
                System.loadLibrary("opencv_java4")
                isOpenCVLoaded = true
                Log.d("StructuralTilt", "✅ OpenCV loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("opencv")
                    isOpenCVLoaded = true
                    Log.d("StructuralTilt", "✅ OpenCV loaded with fallback")
                } catch (e2: UnsatisfiedLinkError) {
                    Log.e("StructuralTilt", "❌ Failed to load OpenCV library", e2)
                    isOpenCVLoaded = false
                }
            }
        }
    }

    /**
     * Enhanced result with camera compensation tracking
     */
    data class StructuralTiltResult(
        val averageVerticalTilt: Float,
        val averageHorizontalTilt: Float,
        val confidence: Float,
        val detectedLines: Int,
        val tiltSeverity: TiltSeverity,
        val cameraTiltCompensation: Float? = null,
        val rawVerticalTilt: Float? = null,
        val rawHorizontalTilt: Float? = null,
        val warning: String? = null
    )

    enum class TiltSeverity {
        NONE,           // <2° - Normal
        MINOR,          // 2-5° - Monitor
        MODERATE,       // 5-10° - Inspection needed
        SEVERE          // >10° - Critical
    }

    /**
     * ✅ MAIN METHOD: Analyze with camera tilt compensation
     */
    fun analyzeWithCompensation(
        bitmap: Bitmap,
        cameraTilt: TiltMeasurement?
    ): StructuralTiltResult {
        if (!isOpenCVLoaded) {
            Log.w("StructuralTilt", "OpenCV not loaded")
            return StructuralTiltResult(
                0f, 0f, 0f, 0, TiltSeverity.NONE,
                warning = "OpenCV not available"
            )
        }

        val rawResult = analyzeStructuralTiltRaw(bitmap)

        return if (cameraTilt != null) {
            compensateForCameraTilt(rawResult, cameraTilt)
        } else {
            rawResult.copy(
                confidence = rawResult.confidence * 0.6f,
                warning = "No camera tilt data - accuracy reduced"
            )
        }
    }

    /**
     * ✅ RECOMMENDED: Analyze multiple photos
     */
    fun analyzeMultipleImages(
        bitmaps: List<Bitmap>,
        cameraTilts: List<TiltMeasurement?>
    ): StructuralTiltResult {
        if (bitmaps.isEmpty()) {
            return StructuralTiltResult(0f, 0f, 0f, 0, TiltSeverity.NONE)
        }

        val results = bitmaps.mapIndexed { index, bitmap ->
            val cameraTilt = cameraTilts.getOrNull(index)
            analyzeWithCompensation(bitmap, cameraTilt)
        }

        val reliable = results.filter { it.confidence > 0.5f }

        if (reliable.isEmpty()) {
            return StructuralTiltResult(
                0f, 0f, 0f, 0, TiltSeverity.NONE,
                warning = "Insufficient reliable measurements"
            )
        }

        val totalWeight = reliable.sumOf { it.confidence.toDouble() }.toFloat()

        val avgVertical = reliable.sumOf {
            (it.averageVerticalTilt * it.confidence).toDouble()
        }.toFloat() / totalWeight

        val avgHorizontal = reliable.sumOf {
            (it.averageHorizontalTilt * it.confidence).toDouble()
        }.toFloat() / totalWeight

        val avgConfidence = reliable.map { it.confidence }.average().toFloat()
        val totalLines = reliable.sumOf { it.detectedLines }

        val severity = classifySeverity(avgVertical)

        Log.d(
            "StructuralTilt",
            "Multi-photo: ${reliable.size}/${bitmaps.size} reliable, " +
                    "Vertical: ${avgVertical.format(2)}°, Confidence: ${(avgConfidence * 100).toInt()}%"
        )

        return StructuralTiltResult(
            averageVerticalTilt = avgVertical,
            averageHorizontalTilt = avgHorizontal,
            confidence = (avgConfidence * 1.2f).coerceAtMost(1.0f),
            detectedLines = totalLines,
            tiltSeverity = severity
        )
    }

    /**
     * Raw structural analysis
     */
    private fun analyzeStructuralTiltRaw(bitmap: Bitmap): StructuralTiltResult {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 150.0, 3, false)

            val lines = Mat()
            val minLineLength = (mat.cols() * 0.1).coerceAtLeast(50.0)

            // ✅ FIXED: No named arguments (OpenCV is Java-based)
            Imgproc.HoughLinesP(
                edges,                  // Input edge image
                lines,                  // Output lines
                1.0,                    // rho: Distance resolution in pixels
                Math.PI / 180,          // theta: Angle resolution in radians
                50,                     // threshold: Minimum votes
                minLineLength,          // minLineLength: Minimum line length
                20.0                    // maxLineGap: Maximum gap between line segments
            )

            val (verticalAngles, horizontalAngles) = classifyLines(lines)
            val cleanVertical = removeOutliers(verticalAngles)
            val cleanHorizontal = removeOutliers(horizontalAngles)

            val avgVertical = cleanVertical.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            val avgHorizontal = cleanHorizontal.average().toFloat().takeIf { !it.isNaN() } ?: 0f

            val severity = classifySeverity(avgVertical)

            val lineCount = cleanVertical.size + cleanHorizontal.size
            val angleStdDev = if (cleanVertical.size > 1) {
                val mean = cleanVertical.average()
                sqrt(cleanVertical.map { (it - mean).pow(2) }.average()).toFloat()
            } else 0f

            val confidence = when {
                lineCount >= 15 && angleStdDev < 2f -> 0.95f
                lineCount >= 10 && angleStdDev < 3f -> 0.85f
                lineCount >= 5 && angleStdDev < 5f -> 0.70f
                lineCount >= 3 -> 0.50f
                else -> 0.30f
            }

            Log.d(
                "StructuralTilt",
                "Raw analysis: $lineCount lines, Vertical: ${avgVertical.format(2)}°, " +
                        "StdDev: ${angleStdDev.format(2)}°, Confidence: ${(confidence * 100).toInt()}%"
            )

            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            lines.release()

            StructuralTiltResult(
                averageVerticalTilt = avgVertical,
                averageHorizontalTilt = avgHorizontal,
                confidence = confidence,
                detectedLines = lineCount,
                tiltSeverity = severity,
                rawVerticalTilt = avgVertical,
                rawHorizontalTilt = avgHorizontal
            )

        } catch (e: Exception) {
            Log.e("StructuralTilt", "Analysis failed", e)
            StructuralTiltResult(0f, 0f, 0f, 0, TiltSeverity.NONE)
        }
    }

    /**
     * Apply camera tilt compensation
     */
    private fun compensateForCameraTilt(
        raw: StructuralTiltResult,
        cameraTilt: TiltMeasurement
    ): StructuralTiltResult {

        val verticalCompensation = abs(cameraTilt.pitch)
        val horizontalCompensation = abs(cameraTilt.roll)

        val correctedVertical = (raw.averageVerticalTilt - verticalCompensation).coerceAtLeast(0f)
        val correctedHorizontal = (raw.averageHorizontalTilt - horizontalCompensation).coerceAtLeast(0f)

        val confidenceMultiplier = when {
            cameraTilt.tiltMagnitude < 3f -> 1.3f
            cameraTilt.tiltMagnitude < 5f -> 1.1f
            cameraTilt.tiltMagnitude < 10f -> 0.9f
            else -> 0.7f
        }

        val severity = classifySeverity(correctedVertical)

        Log.d(
            "StructuralTilt",
            "Compensation: Raw=${raw.averageVerticalTilt.format(2)}°, " +
                    "Camera=${cameraTilt.tiltMagnitude.format(2)}°, " +
                    "Corrected=${correctedVertical.format(2)}°"
        )

        return StructuralTiltResult(
            averageVerticalTilt = correctedVertical,
            averageHorizontalTilt = correctedHorizontal,
            confidence = (raw.confidence * confidenceMultiplier).coerceIn(0f, 1f),
            detectedLines = raw.detectedLines,
            tiltSeverity = severity,
            cameraTiltCompensation = cameraTilt.tiltMagnitude,
            rawVerticalTilt = raw.averageVerticalTilt,
            rawHorizontalTilt = raw.averageHorizontalTilt
        )
    }

    /**
     * Classify lines as vertical or horizontal
     */
    private fun classifyLines(lines: Mat): Pair<List<Double>, List<Double>> {
        val verticalAngles = mutableListOf<Double>()
        val horizontalAngles = mutableListOf<Double>()

        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0]
            val y1 = line[1]
            val x2 = line[2]
            val y2 = line[3]

            val angle = Math.toDegrees(atan2(y2 - y1, x2 - x1))

            var normalizedAngle = angle
            if (normalizedAngle > 90) normalizedAngle -= 180
            if (normalizedAngle < -90) normalizedAngle += 180

            when {
                abs(normalizedAngle) > 70 -> {
                    val deviation = 90 - abs(normalizedAngle)
                    verticalAngles.add(deviation)
                }
                abs(normalizedAngle) < 20 -> {
                    horizontalAngles.add(normalizedAngle)
                }
            }
        }

        return Pair(verticalAngles, horizontalAngles)
    }

    /**
     * Remove statistical outliers
     */
    private fun removeOutliers(angles: List<Double>): List<Double> {
        if (angles.size < 3) return angles

        val mean = angles.average()
        val variance = angles.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        return angles.filter { abs(it - mean) <= 2 * stdDev }
    }

    /**
     * Classify severity
     */
    private fun classifySeverity(tilt: Float): TiltSeverity {
        return when {
            tilt < 2f -> TiltSeverity.NONE
            tilt < 5f -> TiltSeverity.MINOR
            tilt < 10f -> TiltSeverity.MODERATE
            else -> TiltSeverity.SEVERE
        }
    }

    // Public helper methods for UI
    fun getSeverityDescription(severity: TiltSeverity): String {
        return when (severity) {
            TiltSeverity.NONE -> "No structural tilt detected. Building appears level."
            TiltSeverity.MINOR -> "Minor tilt detected (2-5°). Continue monitoring during regular inspections."
            TiltSeverity.MODERATE -> "Moderate tilt detected (5-10°). Professional inspection recommended within 1-3 months."
            TiltSeverity.SEVERE -> "Severe tilt detected (>10°). Contact structural engineer immediately."
        }
    }

    fun getRecommendedActions(severity: TiltSeverity): List<String> {
        return when (severity) {
            TiltSeverity.NONE -> listOf(
                "Continue regular maintenance schedule",
                "No immediate action required"
            )
            TiltSeverity.MINOR -> listOf(
                "Document with photos every 6 months",
                "Mark reference points to track progression",
                "Monitor for cracks or door/window issues"
            )
            TiltSeverity.MODERATE -> listOf(
                "Schedule professional structural inspection",
                "Document current condition thoroughly",
                "Check foundation for settlement or cracks",
                "Monitor for rapid changes weekly"
            )
            TiltSeverity.SEVERE -> listOf(
                "Contact structural engineer within 24-48 hours",
                "Document with dated photos immediately",
                "Assess foundation and soil conditions",
                "Consider temporary support measures",
                "Do not ignore - serious structural concern"
            )
        }
    }

    fun getSeverityColor(severity: TiltSeverity): Int {
        return when (severity) {
            TiltSeverity.NONE -> 0xFF10B981.toInt()
            TiltSeverity.MINOR -> 0xFF3B82F6.toInt()
            TiltSeverity.MODERATE -> 0xFFF59E0B.toInt()
            TiltSeverity.SEVERE -> 0xFFEF4444.toInt()
        }
    }
}

// Extension function for formatting
private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
