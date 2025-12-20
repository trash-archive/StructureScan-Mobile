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
 * ✅ PRODUCTION STRUCTURAL TILT ANALYZER (ATC-20 + ASCE 7 Standards)
 * Tilt thresholds: <0.25°=NONE, 0.25-2°=MINOR, 2-5°=MODERATE, >5°=SEVERE
 */
class StructuralTiltAnalyzer {

    companion object {
        private var isOpenCVLoaded = false

        init {
            try {
                System.loadLibrary("opencv_java4")
                isOpenCVLoaded = true
                Log.d("StructuralTilt", "✅ OpenCV loaded (v4)")
            } catch (e: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("opencv")
                    isOpenCVLoaded = true
                    Log.d("StructuralTilt", "✅ OpenCV loaded (fallback)")
                } catch (e2: UnsatisfiedLinkError) {
                    Log.e("StructuralTilt", "❌ OpenCV FAILED", e2)
                    isOpenCVLoaded = false
                }
            }
        }
    }

    /**
     * ✅ STANDARDIZED RESULT (ATC-20 Rapid Assessment)
     */
    data class StructuralTiltResult(
        val averageVerticalTilt: Float,      // Degrees
        val averageHorizontalTilt: Float,    // Degrees
        val confidence: Float,               // 0.0-1.0
        val detectedLines: Int,
        val tiltSeverity: TiltSeverity,      // ATC-20: NONE/MINOR/MODERATE/SEVERE
        val cameraTiltCompensation: Float? = null,
        val rawVerticalTilt: Float? = null,
        val rawHorizontalTilt: Float? = null,
        val warning: String? = null
    )

    /**
     * ✅ INDUSTRY STANDARD THRESHOLDS (ASCE 7-22 + ATC-20)
     * 0.25° = Code limit (1/500 tilt ratio)
     * 2°    = Minor concern (monitor)
     * 5°    = Moderate (inspect immediately)
     * >5°   = SEVERE (UNSAFE - Red tag)
     */
    enum class TiltSeverity {
        NONE,      // <0.25° - INSPECTED (Green)
        MINOR,     // 0.25-2° - MINOR (Yellow)
        MODERATE,  // 2-5° - RESTRICTED (Orange)
        SEVERE     // >5° - UNSAFE (Red)
    }

    /**
     * ✅ MAIN ENTRY POINT: Single photo with camera compensation
     */
    fun analyzeWithCompensation(
        bitmap: Bitmap,
        cameraTilt: TiltMeasurement? = null
    ): StructuralTiltResult {
        if (!isOpenCVLoaded) {
            return StructuralTiltResult(
                0f, 0f, 0f, 0, TiltSeverity.NONE,
                warning = "OpenCV unavailable"
            )
        }

        val rawResult = analyzeStructuralTiltRaw(bitmap)

        return cameraTilt?.let {
            compensateForCameraTilt(rawResult, it)
        } ?: rawResult.copy(
            confidence = rawResult.confidence * 0.7f,  // No camera data penalty
            warning = "No camera tilt data (reduced accuracy)"
        )
    }

    /**
     * ✅ MULTI-PHOTO ANALYSIS (Recommended for areas)
     */
    fun analyzeMultipleImages(
        bitmaps: List<Bitmap>,
        cameraTilts: List<TiltMeasurement?> = emptyList()
    ): StructuralTiltResult {
        if (bitmaps.isEmpty()) return safeDefault()

        val results = bitmaps.mapIndexed { i, bitmap ->
            val tilt = cameraTilts.getOrNull(i)
            analyzeWithCompensation(bitmap, tilt)
        }

        // Weighted average (confidence-based)
        val reliable = results.filter { it.confidence >= 0.4f }
        if (reliable.isEmpty()) return safeDefault("No reliable measurements")

        val totalWeight = reliable.sumOf { it.confidence.toDouble() }.toFloat()
        // ✅ SIMPLEST FIX - Use fold()
        val avgVertical = reliable.fold(0.0) { acc, it ->
            acc + (it.averageVerticalTilt * it.confidence)
        }.toFloat() / totalWeight

        val avgHorizontal = reliable.fold(0.0) { acc, it ->
            acc + (it.averageHorizontalTilt * it.confidence)
        }.toFloat() / totalWeight

        val avgConfidence = reliable.map { it.confidence }.average().toFloat()
        val totalLines = reliable.sumOf { it.detectedLines }
        val severity = classifySeverity(avgVertical)

        Log.d("StructuralTilt", "📊 Multi: ${reliable.size}/${bitmaps.size} reliable, " +
                "V:${avgVertical.format(2)}° (${severity.name}), Conf:${(avgConfidence*100).toInt()}%")

        return StructuralTiltResult(
            averageVerticalTilt = avgVertical,
            averageHorizontalTilt = avgHorizontal,
            confidence = avgConfidence.coerceAtMost(1f),
            detectedLines = totalLines,
            tiltSeverity = severity
        )
    }

    /**
     * ✅ RISK SCORING SYSTEM INTEGRATION (0-3 points)
     */
    fun getRiskPoints(result: StructuralTiltResult): Float {
        return when (result.tiltSeverity) {
            TiltSeverity.SEVERE -> 3f    // >5° = ATC-20 UNSAFE
            TiltSeverity.MODERATE -> 2f  // 2-5° = RESTRICTED
            TiltSeverity.MINOR -> 1f     // 0.25-2° = MINOR
            TiltSeverity.NONE -> 0f      // <0.25° = INSPECTED
        }
    }

    // ========== PRIVATE IMPLEMENTATION ==========

    private fun safeDefault(warning: String? = null): StructuralTiltResult {
        return StructuralTiltResult(0f, 0f, 0f, 0, TiltSeverity.NONE, warning = warning)
    }

    private fun analyzeStructuralTiltRaw(bitmap: Bitmap): StructuralTiltResult {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Preprocessing pipeline
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val edges = Mat()
            Imgproc.Canny(blurred, edges, 60.0, 180.0, 3, false)

            val lines = Mat()
            val minLineLength = (mat.cols() * 0.08).coerceAtLeast(40.0)

            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 60,
                minLineLength, 15.0)

            val (vertical, horizontal) = classifyLines(lines)
            val cleanVertical = removeOutliers(vertical)
            val cleanHorizontal = removeOutliers(horizontal)

            val avgVertical = cleanVertical.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            val avgHorizontal = cleanHorizontal.average().toFloat().takeIf { !it.isNaN() } ?: 0f

            val severity = classifySeverity(avgVertical)
            val confidence = calculateConfidence(cleanVertical.size + cleanHorizontal.size, avgVertical)

            cleanup(listOf(mat, gray, blurred, edges, lines))

            Log.d("StructuralTilt", "🎯 Single: V:${avgVertical.format(2)}° (${severity.name}), Lines:${cleanVertical.size + cleanHorizontal.size}")

            StructuralTiltResult(
                averageVerticalTilt = avgVertical,
                averageHorizontalTilt = avgHorizontal,
                confidence = confidence,
                detectedLines = cleanVertical.size + cleanHorizontal.size,
                tiltSeverity = severity,
                rawVerticalTilt = avgVertical,
                rawHorizontalTilt = avgHorizontal
            )

        } catch (e: Exception) {
            Log.e("StructuralTilt", "Raw analysis failed", e)
            safeDefault("Analysis error")
        }
    }

    private fun compensateForCameraTilt(raw: StructuralTiltResult, cameraTilt: TiltMeasurement): StructuralTiltResult {
        val verticalComp = abs(cameraTilt.pitch)
        val horizontalComp = abs(cameraTilt.roll)

        val correctedVertical = (raw.averageVerticalTilt - verticalComp).coerceAtLeast(0f)
        val correctedHorizontal = (raw.averageHorizontalTilt - horizontalComp).coerceAtLeast(0f)

        val severity = classifySeverity(correctedVertical)
        val confMultiplier = (1f - (cameraTilt.tiltMagnitude / 20f)).coerceIn(0.6f, 1.3f)

        Log.d("StructuralTilt", "🔧 Comp: ${raw.averageVerticalTilt.format(2)}° → ${correctedVertical.format(2)}° (camera:${cameraTilt.tiltMagnitude.format(1)}°)")

        return StructuralTiltResult(
            averageVerticalTilt = correctedVertical,
            averageHorizontalTilt = correctedHorizontal,
            confidence = (raw.confidence * confMultiplier).coerceIn(0f, 1f),
            detectedLines = raw.detectedLines,
            tiltSeverity = severity,
            cameraTiltCompensation = cameraTilt.tiltMagnitude,
            rawVerticalTilt = raw.averageVerticalTilt,
            rawHorizontalTilt = raw.averageHorizontalTilt
        )
    }

    private fun classifyLines(lines: Mat): Pair<List<Double>, List<Double>> {
        val vertical = mutableListOf<Double>()
        val horizontal = mutableListOf<Double>()

        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val angle = Math.toDegrees(atan2(line[3] - line[1], line[2] - line[0]))

            val normalized = if (angle > 90) angle - 180 else if (angle < -90) angle + 180 else angle

            when {
                abs(normalized) > 65 -> vertical.add(90 - abs(normalized))  // Near-vertical
                abs(normalized) < 25 -> horizontal.add(abs(normalized))      // Near-horizontal
            }
        }
        return Pair(vertical, horizontal)
    }

    private fun removeOutliers(angles: List<Double>): List<Double> {
        if (angles.size < 3) return angles
        val mean = angles.average()
        val stdDev = sqrt(angles.map { (it - mean).pow(2) }.average())
        return angles.filter { abs(it - mean) <= 2 * stdDev }
    }

    /**
     * ✅ ATC-20 STANDARD THRESHOLDS
     * 0.25° = Building code limit (1/500)
     * 2°    = Monitor (engineering evaluation)
     * 5°    = Restricted use (immediate inspection)
     * >5°   = Unsafe (evacuate)
     */
    private fun classifySeverity(tiltDegrees: Float): TiltSeverity = when {
        tiltDegrees < 0.25f -> TiltSeverity.NONE
        tiltDegrees < 2f -> TiltSeverity.MINOR
        tiltDegrees < 5f -> TiltSeverity.MODERATE
        else -> TiltSeverity.SEVERE
    }

    private fun calculateConfidence(lineCount: Int, tilt: Float): Float {
        val baseConf = when {
            lineCount >= 20 -> 0.95f
            lineCount >= 12 -> 0.85f
            lineCount >= 6 -> 0.70f
            lineCount >= 3 -> 0.50f
            else -> 0.25f
        }
        return (baseConf * (1f - tilt / 20f)).coerceIn(0.1f, 1f)
    }

    private fun cleanup(mats: List<Mat>) = mats.forEach { it.release() }

    // UI Helpers
    fun getSeverityDescription(severity: TiltSeverity): String = when (severity) {
        TiltSeverity.NONE -> "✅ Level (<0.25°)"
        TiltSeverity.MINOR -> "🟡 Minor (0.25-2°)"
        TiltSeverity.MODERATE -> "🟠 Moderate (2-5°)"
        TiltSeverity.SEVERE -> "🔴 SEVERE (>5°)"
    }
}

// Formatting extension
private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
