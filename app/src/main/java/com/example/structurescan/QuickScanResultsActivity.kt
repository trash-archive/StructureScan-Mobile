package com.example.structurescan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

// -----------------------------------------------------------------------------
// Data models
// -----------------------------------------------------------------------------

data class QuickScanDetectedIssue(
    val damageType: String,
    val damageLevel: String,   // "High", "Moderate", "Low"
    val confidence: Float
)

/**
 * severity: "HIGH" / "MODERATE" / "LOW" derived from damageLevel
 * This will drive the recommendation card color and chip label.
 */
data class QuickScanRecommendation(
    val title: String,
    val simplifiedDescription: String,
    val actions: List<String>,
    val severity: String       // "HIGH","MODERATE","LOW"
)

data class QuickScanItem(
    val imageUri: String,
    val detectedIssues: List<QuickScanDetectedIssue>,
    val imageRisk: String,       // overall image risk
    val confidence: Float,
    val recommendations: List<QuickScanRecommendation>
)

data class QuickScanAssessment(
    val imageUri: Uri,
    val damageType: String,
    val damageLevel: String,
    val confidence: Float,
    val detectedIssues: List<QuickScanDetectedIssue>,
    val imageRisk: String
)

// -----------------------------------------------------------------------------
// Activity
// -----------------------------------------------------------------------------

class QuickScanResultsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUris = intent.getStringArrayListExtra("QUICK_SCAN_URIS") ?: arrayListOf()
        setContent {
            MaterialTheme {
                QuickScanResultsScreen(imageUris, this)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen with pager + FAB
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScanResultsScreen(imageUris: List<String>, context: Context) {
    var results by remember { mutableStateOf<List<QuickScanItem>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(true) }

    LaunchedEffect(imageUris) {
        isAnalyzing = true
        results = withContext(Dispatchers.IO) {
            imageUris.mapNotNull { uriString ->
                val uri = Uri.parse(uriString)
                quickScanAnalyzeImage(context, uri)?.let { assessment ->
                    QuickScanItem(
                        imageUri = uriString,
                        detectedIssues = assessment.detectedIssues,
                        imageRisk = assessment.imageRisk,
                        confidence = assessment.confidence,
                        recommendations = assessment.detectedIssues.mapNotNull { issue ->
                            quickScanGetRecommendationSimple(issue.damageType, issue.damageLevel)
                        }
                    )
                }
            }
        }
        isAnalyzing = false
    }

    val pagerState = rememberPagerState(pageCount = { results.size.coerceAtLeast(1) })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Quick Scan Results",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { (context as ComponentActivity).finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (results.isNotEmpty()) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${results.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { (context as ComponentActivity).finish() }, // or open camera again
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Scan again")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            when {
                isAnalyzing -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                        Spacer(Modifier.height(20.dp))
                        Text("Analyzing...", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }

                results.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF10B981)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "All Good!",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No issues detected in your images",
                            fontSize = 15.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        QuickScanResultDetail(result = results[page])
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Detail page for a single image
// -----------------------------------------------------------------------------

@Composable
fun QuickScanResultDetail(result: QuickScanItem) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {

        // Image
        item {
            AsyncImage(
                model = result.imageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Issues Detected
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Issues Detected",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result.detectedIssues.isEmpty()) {
                    IssueEmptyRow()
                } else {
                    result.detectedIssues.forEach { issue ->
                        IssueChipRow(issue)
                    }
                }
            }
        }

        // Recommendations
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Recommendations",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            if (result.recommendations.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    result.recommendations.forEach { rec ->
                        RecommendationCardScreenshotStyle(
                            recommendation = rec,
                            // here you could pass count & confidence if you want
                            imageCount = 1,
                            aiConfidence = (result.confidence * 100f)
                        )
                    }
                }
            } else {
                RecommendationEmptyCard()
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Recommendation card – copied style from screenshot
// -----------------------------------------------------------------------------

// Colors for HIGH / MODERATE / LOW tag and border
data class SeverityColors(
    val labelText: Color,
    val labelBg: Color,
    val border: Color
)

fun severityColors(severity: String): SeverityColors {
    return when (severity.uppercase()) {
        "HIGH" -> SeverityColors(
            labelText = Color(0xFFB71C1C),
            labelBg = Color(0xFFFFEBEE),
            border = Color(0xFFFFCDD2)
        )
        "MODERATE" -> SeverityColors(
            labelText = Color(0xFFF57C00),
            labelBg = Color(0xFFFFF3E0),
            border = Color(0xFFFFE0B2)
        )
        else -> SeverityColors(
            labelText = Color(0xFF2E7D32),
            labelBg = Color(0xFFE8F5E9),
            border = Color(0xFFC8E6C9)
        )
    }
}

@Composable
fun RecommendationCardScreenshotStyle(
    recommendation: QuickScanRecommendation,
    imageCount: Int,
    aiConfidence: Float
) {
    val sevColors = severityColors(recommendation.severity)

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, sevColors.border.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Title row + severity chip (e.g. LOW / HIGH)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(4.dp))

                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(sevColors.labelBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recommendation.severity,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = sevColors.labelText
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Grey description block
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF3F4F6),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = recommendation.simplifiedDescription,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Numbered action steps
            recommendation.actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(sevColors.labelText),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = action,
                        fontSize = 14.sp,
                        color = Color(0xFF4B5563)
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "No action required. Surface appears in good condition.",
                fontSize = 14.sp,
                color = Color(0xFF4B5563)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Issues UI
// -----------------------------------------------------------------------------

@Composable
fun IssueChipRow(issue: QuickScanDetectedIssue) {
    val (mainColor, bgColor) = riskColors(
        when (issue.damageLevel) {
            "High" -> "SEVERE"
            "Moderate" -> "MODERATE"
            else -> "MINOR"
        }
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(mainColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = issue.damageType,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color(0xFF111827)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "AI Confidence:",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
                Text(
                    text = "${String.format("%.0f", issue.confidence * 100)}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = mainColor
                )
            }
        }
    }
}

@Composable
fun IssueEmptyRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE8F5E9),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "No visible issues found in this image",
                fontSize = 14.sp,
                color = Color(0xFF4B5563)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Risk helpers
// -----------------------------------------------------------------------------

fun riskColors(risk: String): Pair<Color, Color> {
    val main = when (risk) {
        "SEVERE" -> Color(0xFFD32F2F)
        "MODERATE" -> Color(0xFFF57C00)
        "MINOR" -> Color(0xFF388E3C)
        "INSUFFICIENT" -> Color(0xFF6B7280)
        else -> Color(0xFF388E3C)
    }
    val bg = when (risk) {
        "SEVERE" -> Color(0xFFFFEBEE)
        "MODERATE" -> Color(0xFFFFF3E0)
        "MINOR" -> Color(0xFFE8F5E9)
        "INSUFFICIENT" -> Color(0xFFF1F5F9)
        else -> Color(0xFFE8F5E9)
    }
    return main to bg
}

// -----------------------------------------------------------------------------
// Recommendation mapping: now also sets severity per damage level
// -----------------------------------------------------------------------------

fun quickScanGetRecommendationSimple(
    damageType: String,
    damageLevel: String
): QuickScanRecommendation {
    val severity = when (damageLevel) {
        "High" -> "HIGH"
        "Moderate" -> "MODERATE"
        else -> "LOW"
    }

    val key = "$damageType-$damageLevel"
    return when (key) {
        "Spalling-High" -> QuickScanRecommendation(
            "Serious Concrete Damage",
            "Concrete is breaking away from the surface, possibly exposing metal bars inside. This needs urgent attention from a building expert.",
            listOf(
                "Call a structural engineer or building expert within 2-3 days",
                "Take clear photos of the damaged area from different angles",
                "Check if you can see any metal bars (rebar) showing through - avoid using this area",
                "Measure the damage - if deeper than 1 inch or larger than your hand, it needs professional repair",
                "Professional will remove damaged concrete, clean metal bars, fill with repair cement",
                "After repair, seal the surface to protect it from water and prevent future damage"
            ),
            severity
        )

        "Major Crack-High" -> QuickScanRecommendation(
            "Large Crack Found",
            "Wide crack detected (wider than 3mm or about 1/8 inch). This could mean the foundation is settling or the structure is under stress.",
            listOf(
                "Contact a structural engineer or building expert within 1-2 weeks",
                "Put markers on both sides of the crack to see if it's getting bigger",
                "Measure and photograph the crack - note how wide, how long, and where it is",
                "Check if doors or windows are sticking, or if floors are sloping",
                "Expert may inject special material to fill the crack or strengthen the structure",
                "Seal the crack after repair to keep water out and prevent freeze damage"
            ),
            severity
        )

        "Algae-Moderate" -> QuickScanRecommendation(
            "Algae/Moss Growth",
            "Algae or moss growing on the building means there's too much moisture. Not immediately dangerous, but can damage materials over time.",
            listOf(
                "Clean the area within 1-2 months using algae remover or cleaning solution",
                "Gently wash with garden hose and soft brush - don't use a pressure washer",
                "Find and fix why it's wet (drainage, gutters, roof leaks)",
                "Cut back trees and bushes so more sunlight reaches the wall",
                "Apply a protective coating to slow algae growth",
                "Re-check the area in 6-12 months"
            ),
            severity
        )

        "Minor Crack-Low" -> QuickScanRecommendation(
            "Small Hairline Cracks",
            "Thin cracks found - these are common as buildings settle and concrete dries. Usually not serious, but keep an eye on them.",
            listOf(
                "Check these cracks once or twice a year during inspections",
                "Monitor if the crack widens over 6-12 months",
                "Seal the cracks during scheduled maintenance",
                "Use flexible crack filler suitable for the location",
                "No structural concern unless they grow significantly"
            ),
            severity
        )

        "Paint Damage-Low" -> QuickScanRecommendation(
            "Paint Peeling or Flaking",
            "Paint is coming off the surface. Usually caused by water damage or old paint. Mostly cosmetic, but fix the water source first.",
            listOf(
                "Plan to repaint within 12-24 months during regular maintenance",
                "Find and fix the water problem first (leaks, bad drainage, humidity)",
                "Scrape off loose paint, clean the surface, apply primer, then paint",
                "Choose the right paint for the area (mildew‑resistant or weather‑resistant)",
                "Treat as a maintenance item – no safety concerns"
            ),
            severity
        )

        else -> QuickScanRecommendation(
            "Good Condition",
            "No structural damage or surface deterioration detected. Building surface appears well-maintained.",
            listOf(
                "Follow your regular inspection schedule",
                "Keep drainage and moisture under control",
                "Note any new cracks or discoloration during future checks"
            ),
            "LOW"
        )
    }
}

// -----------------------------------------------------------------------------
// TensorFlow analysis (unchanged)
// -----------------------------------------------------------------------------

suspend fun quickScanAnalyzeImage(context: Context, imageUri: Uri): QuickScanAssessment? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext null

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 224, 224, true)
            val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3).apply {
                order(ByteOrder.nativeOrder())
            }

            val intValues = IntArray(224 * 224)
            resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                    byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                    byteBuffer.putFloat((value and 0xFF) / 255.0f)
                }
            }

            val model = com.example.structurescan.ml.ModelUnquant.newInstance(context)
            val inputFeature0 = TensorBuffer.createFixedSize(
                intArrayOf(1, 224, 224, 3),
                DataType.FLOAT32
            )
            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray

            val crackHigh = confidences.getOrNull(0) ?: 0f
            val crackMod = confidences.getOrNull(1) ?: 0f
            val crackLow = confidences.getOrNull(2) ?: 0f
            val paintConf = confidences.getOrNull(3) ?: 0f
            val algaeConf = confidences.getOrNull(4) ?: 0f
            val plainConf = confidences.getOrNull(5) ?: 0f

            val SHOW_THRESHOLD = 0.50f
            val detectedIssues = mutableListOf<QuickScanDetectedIssue>()

            if (crackHigh >= SHOW_THRESHOLD)
                detectedIssues.add(QuickScanDetectedIssue("Spalling", "High", crackHigh))
            if (crackMod >= SHOW_THRESHOLD)
                detectedIssues.add(QuickScanDetectedIssue("Major Crack", "High", crackMod))
            if (crackLow >= SHOW_THRESHOLD)
                detectedIssues.add(QuickScanDetectedIssue("Minor Crack", "Low", crackLow))
            if (paintConf >= SHOW_THRESHOLD)
                detectedIssues.add(QuickScanDetectedIssue("Paint Damage", "Low", paintConf))
            if (algaeConf >= SHOW_THRESHOLD)
                detectedIssues.add(QuickScanDetectedIssue("Algae", "Moderate", algaeConf))

            val maxConfidence = detectedIssues.maxOfOrNull { it.confidence } ?: plainConf
            val imageRisk = when {
                detectedIssues.any { it.damageType == "Spalling" || it.damageType == "Major Crack" } -> "SEVERE"
                detectedIssues.any { it.damageType == "Algae" } -> "MODERATE"
                detectedIssues.isNotEmpty() -> "MINOR"
                else -> "GOOD"
            }

            model.close()
            originalBitmap.recycle()
            resizedBitmap.recycle()

            QuickScanAssessment(
                imageUri = imageUri,
                damageType = detectedIssues.firstOrNull()?.damageType ?: "Plain",
                damageLevel = detectedIssues.firstOrNull()?.damageLevel ?: "None",
                confidence = maxConfidence,
                detectedIssues = detectedIssues,
                imageRisk = imageRisk
            )
        } catch (e: Exception) {
            Log.e("QuickScanTF", "Analysis failed", e)
            null
        }
    }
}
