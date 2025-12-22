package com.example.structurescan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Quick Tilt Result Activity
 * Displays instant tilt analysis results without database storage
 */
class QuickTiltResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val verticalTilt = intent.getFloatExtra("VERTICAL_TILT", 0f)
        val horizontalTilt = intent.getFloatExtra("HORIZONTAL_TILT", 0f)
        val confidence = intent.getFloatExtra("CONFIDENCE", 0f)
        val detectedLines = intent.getIntExtra("DETECTED_LINES", 0)
        val severity = intent.getStringExtra("SEVERITY") ?: "NONE"
        val cameraComp = intent.getFloatExtra("CAMERA_COMPENSATION", 0f)
        val rawVertical = intent.getFloatExtra("RAW_VERTICAL", 0f)
        val rawHorizontal = intent.getFloatExtra("RAW_HORIZONTAL", 0f)
        val warning = intent.getStringExtra("WARNING")

        setContent {
            MaterialTheme {
                QuickTiltResultScreen(
                    verticalTilt = verticalTilt,
                    horizontalTilt = horizontalTilt,
                    confidence = confidence,
                    detectedLines = detectedLines,
                    severity = severity,
                    cameraCompensation = cameraComp,
                    rawVertical = rawVertical,
                    rawHorizontal = rawHorizontal,
                    warning = warning,
                    onDone = { finish() },
                    onScanAgain = {
                        finish()
                        // Activity will reopen from dashboard
                    }
                )
            }
        }
    }
}

@Composable
fun QuickTiltResultScreen(
    verticalTilt: Float,
    horizontalTilt: Float,
    confidence: Float,
    detectedLines: Int,
    severity: String,
    cameraCompensation: Float,
    rawVertical: Float,
    rawHorizontal: Float,
    warning: String?,
    onDone: () -> Unit,
    onScanAgain: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Severity color and icon
    val (severityColor, severityIcon, severityLabel, severityDescription) = when (severity) {
        "NONE" -> listOf(
            Color(0xFF4CAF50),
            Icons.Filled.CheckCircle,
            "✅ LEVEL",
            "Structure appears level (<0.25°). No tilt concerns detected."
        )
        "MINOR" -> listOf(
            Color(0xFFFFC107),
            Icons.Filled.Warning,
            "🟡 MINOR TILT",
            "Slight tilt detected (0.25-2°). Monitor over time. Generally acceptable."
        )
        "MODERATE" -> listOf(
            Color(0xFFFF9800),
            Icons.Filled.Warning,
            "🟠 MODERATE TILT",
            "Noticeable tilt (2-5°). Professional inspection recommended."
        )
        "SEVERE" -> listOf(
            Color(0xFFF44336),
            Icons.Filled.Error,
            "🔴 SEVERE TILT",
            "Significant tilt detected (>5°). Immediate professional evaluation required!"
        )
        else -> listOf(Color.Gray, Icons.Filled.Warning, "UNKNOWN", "Analysis incomplete")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Quick Tilt Analysis",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C3D6C)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Instant structural tilt assessment",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Main Result Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = severityColor as Color),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = severityIcon as ImageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = severityLabel as String,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Overall out-of-plumb tilt based on your photo",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = String.format("%.2f° vertical tilt", verticalTilt),
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = severityDescription as String,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detailed Measurements Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Detailed Measurements",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(16.dp))

                MeasurementRow("Vertical Tilt", String.format("%.2f°", verticalTilt))
                Spacer(modifier = Modifier.height(8.dp))
                MeasurementRow("Horizontal Tilt", String.format("%.2f°", horizontalTilt))
                Spacer(modifier = Modifier.height(8.dp))
                MeasurementRow("Confidence", String.format("%.0f%%", confidence * 100))
                Spacer(modifier = Modifier.height(8.dp))
                MeasurementRow("Lines Detected", detectedLines.toString())

                if (cameraCompensation > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MeasurementRow(
                        "Camera Compensation",
                        String.format("%.1f°", cameraCompensation)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Technical Details Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Analysis Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "• Method: OpenCV HoughLinesP edge detection",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
                Text(
                    text = "• Standards: ATC-20 + ASCE 7-22",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
                Text(
                    text = "• Thresholds: <0.25°=None, 0.25-2°=Minor, 2-5°=Moderate, >5°=Severe",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )

                if (cameraCompensation > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Camera tilt compensated using device sensors",
                        fontSize = 13.sp,
                        color = Color(0xFF4CAF50),
                        lineHeight = 20.sp
                    )
                }

                if (warning != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $warning",
                        fontSize = 13.sp,
                        color = Color(0xFFFF9800),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recommendation Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0)
            ),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "💡 Recommendation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val recommendation = when (severity) {
                    "NONE" -> "Structure appears level. Continue normal monitoring."
                    "MINOR" -> "Take photos from multiple angles for a complete assessment. Monitor periodically."
                    "MODERATE" -> "Consider a full structural assessment. Multiple photos from different angles recommended."
                    "SEVERE" -> "Professional structural engineer inspection REQUIRED immediately. Consider evacuation if occupied."
                    else -> "Retake photos for better analysis."
                }

                Text(
                    text = recommendation,
                    fontSize = 14.sp,
                    color = Color(0xFFE65100),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF0288D1)
                )
            ) {
                Text(
                    "Scan Again",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0288D1)
                )
            ) {
                Text(
                    "Done",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Disclaimer
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEEEEEE)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "⚠️ Disclaimer: This is a quick assessment tool. For critical structural decisions, always consult a licensed structural engineer.",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MeasurementRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}