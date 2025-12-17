package com.example.structurescan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.structurescan.ml.ModelUnquant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

data class DetectedIssue(
    val damageType: String,
    val damageLevel: String,
    val confidence: Float
)

data class ImageAssessment(
    val imageUri: Uri,
    val damageType: String,
    val damageLevel: String,
    val confidence: Float,
    val firebaseImageUrl: String = "",
    val detectedIssues: List<DetectedIssue> = emptyList(),
    val imageRisk: String = "Low"
)

data class AreaAnalysis(
    val areaId: String,
    val areaName: String,
    val imageAssessments: List<ImageAssessment>,
    val areaRisk: String
)

data class DetectionSummaryItem(
    val damageType: String,
    val count: Int,
    val avgConfidence: Float
)

data class AssessmentSummary(
    val overallRisk: String,
    val totalIssues: Int,
    val detectionSummary: List<DetectionSummaryItem>,
    val areaAnalyses: List<AreaAnalysis>
)

data class DamageRecommendation(
    val title: String,
    val description: String,
    val severity: String,
    val actions: List<String>,
    val severityColor: Color,
    val severityBgColor: Color
)

class AssessmentResultsActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var currentAssessmentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"
        val buildingAreas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, BuildingArea::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS)
        } ?: arrayListOf()

        val buildingType = intent.getStringExtra(IntentKeys.BUILDING_TYPE) ?: ""
        val constructionYear = intent.getStringExtra(IntentKeys.CONSTRUCTION_YEAR) ?: ""
        val renovationYear = intent.getStringExtra(IntentKeys.RENOVATION_YEAR) ?: ""
        val floors = intent.getStringExtra(IntentKeys.FLOORS) ?: ""
        val material = intent.getStringExtra(IntentKeys.MATERIAL) ?: ""
        val foundation = intent.getStringExtra(IntentKeys.FOUNDATION) ?: ""
        val environment = intent.getStringExtra(IntentKeys.ENVIRONMENT) ?: ""
        val previousIssues = intent.getStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES) ?: arrayListOf()
        val occupancy = intent.getStringExtra(IntentKeys.OCCUPANCY) ?: ""
        val environmentalRisks = intent.getStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS) ?: arrayListOf()
        val notes = intent.getStringExtra(IntentKeys.NOTES) ?: ""

        setContent {
            MaterialTheme {
                AssessmentResultsScreen(
                    buildingAreas = buildingAreas,
                    assessmentName = assessmentName,
                    buildingType = buildingType,
                    constructionYear = constructionYear,
                    renovationYear = renovationYear,
                    floors = floors,
                    material = material,
                    foundation = foundation,
                    environment = environment,
                    previousIssues = previousIssues,
                    occupancy = occupancy,
                    environmentalRisks = environmentalRisks,
                    notes = notes,
                    onSaveToFirebase = { summary ->
                        saveAssessmentToFirebase(
                            assessmentName, summary, buildingType, constructionYear,
                            renovationYear, floors, material, foundation, environment,
                            previousIssues, occupancy, environmentalRisks, notes
                        )
                    }
                )
            }
        }
    }

    private suspend fun uploadImageToStorage(imageUri: Uri, userId: String, assessmentId: String, areaId: String, imageIndex: Int): String? {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap == null) return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageData = baos.toByteArray()
            bitmap.recycle()

            val storageRef = storage.reference.child("users/$userId/assessments/$assessmentId/$areaId/image_$imageIndex.jpg")
            storageRef.putBytes(imageData).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "uploadImageToStorage failed", e)
            null
        }
    }

    fun saveAssessmentToFirebase(
        assessmentName: String,
        summary: AssessmentSummary,
        buildingType: String,
        constructionYear: String,
        renovationYear: String,
        floors: String,
        material: String,
        foundation: String,
        environment: String,
        previousIssues: List<String>,
        occupancy: String,
        environmentalRisks: List<String>,
        notes: String,
        isReanalysis: Boolean = false
    ): Boolean {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            return false
        }
        val userId = currentUser.uid

        return try {
            val assessmentId = if (isReanalysis && currentAssessmentId != null) {
                currentAssessmentId!!
            } else {
                UUID.randomUUID().toString().also { currentAssessmentId = it }
            }

            val areasData = mutableListOf<HashMap<String, Any>>()

            summary.areaAnalyses.forEach { areaAnalysis ->
                val imagesData = mutableListOf<HashMap<String, Any>>()

                areaAnalysis.imageAssessments.forEachIndexed { index, assessment ->
                    val firebaseImageUrl = kotlinx.coroutines.runBlocking {
                        uploadImageToStorage(assessment.imageUri, userId, assessmentId, areaAnalysis.areaId, index)
                    } ?: throw Exception("Failed to upload image ${index + 1}")

                    val recommendationsForImage = if (assessment.detectedIssues.isEmpty()) {
                        listOf(hashMapOf(
                            "title" to "Clean Surface",
                            "description" to "No structural damage or surface deterioration detected.",
                            "severity" to "GOOD",
                            "actions" to listOf(
                                "Continue regular maintenance schedule",
                                "Monitor during routine inspections",
                                "No immediate action required"
                            )
                        ))
                    } else {
                        assessment.detectedIssues.map { issue ->
                            val rec = getRecommendation(issue.damageType, issue.damageLevel)
                            hashMapOf(
                                "title" to rec.title,
                                "description" to rec.description,
                                "severity" to rec.severity,
                                "actions" to rec.actions
                            )
                        }
                    }

                    imagesData.add(hashMapOf(
                        "damageType" to assessment.damageType,
                        "damageLevel" to assessment.damageLevel,
                        "confidence" to assessment.confidence,
                        "imageUri" to firebaseImageUrl,
                        "localImageUri" to assessment.imageUri.toString(),
                        "detectedIssues" to assessment.detectedIssues.map {
                            mapOf("type" to it.damageType, "level" to it.damageLevel, "confidence" to it.confidence)
                        },
                        "imageRisk" to assessment.imageRisk,
                        "recommendations" to recommendationsForImage
                    ))
                }

                areasData.add(hashMapOf(
                    "areaId" to areaAnalysis.areaId,
                    "areaName" to areaAnalysis.areaName,
                    "areaRisk" to areaAnalysis.areaRisk,
                    "images" to imagesData
                ))
            }

            val detectionSummaryData = summary.detectionSummary.map { item ->
                hashMapOf(
                    "damageType" to item.damageType,
                    "count" to item.count,
                    "avgConfidence" to item.avgConfidence
                )
            }

            val assessmentData = hashMapOf(
                "assessmentId" to assessmentId,
                "assessmentName" to assessmentName,
                "userId" to userId,
                "timestamp" to System.currentTimeMillis(),
                "date" to SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()),
                "overallRisk" to summary.overallRisk,
                "totalIssues" to summary.totalIssues,
                "detectionSummary" to detectionSummaryData,
                "areas" to areasData,
                "buildingType" to buildingType,
                "constructionYear" to constructionYear,
                "renovationYear" to renovationYear,
                "floors" to floors,
                "material" to material,
                "foundation" to foundation,
                "environment" to environment,
                "previousIssues" to previousIssues,
                "occupancy" to occupancy,
                "environmentalRisks" to environmentalRisks,
                "notes" to notes
            )

            kotlinx.coroutines.runBlocking {
                firestore.collection("users").document(userId).collection("assessments").document(assessmentId).set(assessmentData).await()
            }
            true
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Upload failed", e)
            false
        }
    }

    fun getRecommendation(damageType: String, damageLevel: String): DamageRecommendation {
        val key = "$damageType-$damageLevel"
        return when (key) {
            "Spalling-High" -> DamageRecommendation(
                "Serious Concrete Damage",
                "Concrete is breaking away from the surface, possibly exposing metal bars inside. This needs urgent attention from a building expert.",
                "HIGH",
                listOf(
                    "Call a structural engineer or building expert within 2-3 days",
                    "Take clear photos of the damaged area from different angles",
                    "Check if you can see any metal bars (rebar) showing through - avoid using this area",
                    "Measure the damage - if deeper than 1 inch or larger than your hand, it needs professional repair",
                    "Professional will: remove damaged concrete, clean metal bars, fill with repair cement",
                    "After repair: seal the surface to protect it from water and prevent future damage"
                ),
                Color(0xFFD32F2F),
                Color(0xFFFFEBEE)
            )

            "Major Crack-High" -> DamageRecommendation(
                "Large Crack Found",
                "Wide crack detected (wider than 3mm or about 1/8 inch). This could mean the foundation is settling or the structure is under stress.",
                "HIGH",
                listOf(
                    "Contact a structural engineer or building expert within 1-2 weeks",
                    "Put markers on both sides of the crack to see if it's getting bigger",
                    "Measure and photograph the crack - note how wide, how long, and where it is",
                    "Check if doors or windows are sticking, or if floors are sloping",
                    "Expert may inject special material to fill the crack or strengthen the structure",
                    "Seal the crack after repair to keep water out and prevent freeze damage"
                ),
                Color(0xFFD32F2F),
                Color(0xFFFFEBEE)
            )

            "Minor Crack-Low" -> DamageRecommendation(
                "Small Hairline Crack/s",
                "Thin cracks found - these are common as buildings settle and concrete dries. Usually not serious, but keep an eye on them.",
                "LOW",
                listOf(
                    "Check these cracks once or twice a year during regular building inspections",
                    "Watch if the crack gets bigger over 6-12 months - mark the ends and take photos with a ruler",
                    "Fill the cracks during your next scheduled maintenance to stop water getting in",
                    "Use flexible crack filler that works for indoor or outdoor use",
                    "No need to worry - these small cracks are normal in concrete and brick buildings"
                ),
                Color(0xFF388E3C),
                Color(0xFFE8F5E9)
            )

            "Paint Damage-Low" -> DamageRecommendation(
                "Paint Peeling or Flaking",
                "Paint is coming off the surface. Usually caused by water damage or old paint. Mostly cosmetic, but fix the water source first.",
                "LOW",
                listOf(
                    "Plan to repaint within 12-24 months during regular maintenance",
                    "Find and fix the water problem FIRST: look for leaks, bad drainage, or too much humidity",
                    "Proper fix steps: scrape off loose paint, clean the surface, apply primer, then paint",
                    "Choose the right paint: mildew-resistant for bathrooms/kitchens, weather-resistant for outside",
                    "This is a cosmetic issue - no safety concerns, just maintenance needed"
                ),
                Color(0xFF388E3C),
                Color(0xFFE8F5E9)
            )

            "Algae-Moderate" -> DamageRecommendation(
                "Algae/Moss Growth",
                "Algae or moss growing on the building means there's too much moisture. Not immediately dangerous, but can damage materials over time.",
                "MODERATE",
                listOf(
                    "Clean the area within 1-2 months using algae remover or cleaning solution",
                    "Cleaning method: gently wash with garden hose and soft brush - DON'T use pressure washer",
                    "Find and fix why it's wet: improve drainage, fix gutters, repair any roof leaks",
                    "Cut back trees and bushes so more sunlight reaches the wall and air can flow",
                    "You can apply special coating to prevent algae from growing back",
                    "Check again in 6-12 months to make sure the moisture problem is fixed"
                ),
                Color(0xFFF57C00),
                Color(0xFFFFF3E0)
            )

            else -> DamageRecommendation(
                "Clean Surface",
                "No structural damage or surface deterioration detected. Building surface appears well-maintained and in good condition.",
                "GOOD",
                listOf(
                    "Continue regular maintenance schedule (annual or bi-annual inspections)",
                    "Monitor during routine inspections for any emerging issues",
                    "Maintain proper drainage and moisture control measures",
                    "No immediate action required - building surface in good condition"
                ),
                Color(0xFF2E7D32),
                Color(0xFFE8F5E9)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentResultsScreen(
    buildingAreas: List<BuildingArea>,
    assessmentName: String = "Unnamed Assessment",
    buildingType: String = "",
    constructionYear: String = "",
    renovationYear: String = "",
    floors: String = "",
    material: String = "",
    foundation: String = "",
    environment: String = "",
    previousIssues: List<String> = emptyList(),
    occupancy: String = "",
    environmentalRisks: List<String> = emptyList(),
    notes: String = "",
    onSaveToFirebase: (AssessmentSummary) -> Boolean = { false }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var assessmentSummary by remember { mutableStateOf<AssessmentSummary?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var isSavedToFirebase by remember { mutableStateOf(false) }
    var showReanalyzeDialog by remember { mutableStateOf(false) }
    var hasReanalyzed by remember { mutableStateOf(false) }

    val SHOW_THRESHOLD = 0.50f

    BackHandler(enabled = true) {
        if (isSaving) {
            // Block back button during saving
        } else {
            val intent = Intent(context, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
    }

    fun analyzeImageWithTensorFlow(imageUri: Uri): ImageAssessment? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (originalBitmap == null) throw Exception("Failed to load image")

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 224, 224, true)
            val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3).apply { order(ByteOrder.nativeOrder()) }

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

            val model = ModelUnquant.newInstance(context)
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            inputFeature0.loadBuffer(byteBuffer)
            val confidences = model.process(inputFeature0).outputFeature0AsTensorBuffer.floatArray

            val crackHigh = confidences.getOrNull(0) ?: 0f
            val crackMod = confidences.getOrNull(1) ?: 0f
            val crackLow = confidences.getOrNull(2) ?: 0f
            val paintConf = confidences.getOrNull(3) ?: 0f
            val algaeConf = confidences.getOrNull(4) ?: 0f
            val plainConf = confidences.getOrNull(5) ?: 0f

            val maxIndex = confidences.indices.maxByOrNull { confidences[it] } ?: 0
            val maxConfidence = confidences.getOrNull(maxIndex) ?: 0f

            val (primaryDamageType, primaryDamageLevel) = when (maxIndex) {
                0 -> "Spalling" to "High"
                1 -> "Major Crack" to "High"
                2 -> "Minor Crack" to "Low"
                3 -> "Paint Damage" to "Low"
                4 -> "Algae" to "Moderate"
                5 -> "Plain" to "None"
                else -> "Plain" to "None"
            }

            val detectedIssuesMutable = mutableListOf<DetectedIssue>()

            if (crackHigh > SHOW_THRESHOLD) {
                detectedIssuesMutable.add(DetectedIssue("Spalling", "High", crackHigh))
            }
            if (crackMod > SHOW_THRESHOLD) {
                detectedIssuesMutable.add(DetectedIssue("Major Crack", "High", crackMod))
            }
            if (crackLow > SHOW_THRESHOLD) {
                detectedIssuesMutable.add(DetectedIssue("Minor Crack", "Low", crackLow))
            }
            if (paintConf > SHOW_THRESHOLD) {
                detectedIssuesMutable.add(DetectedIssue("Paint Damage", "Low", paintConf))
            }
            if (algaeConf > SHOW_THRESHOLD) {
                detectedIssuesMutable.add(DetectedIssue("Algae", "Moderate", algaeConf))
            }

            val imageRisk = when {
                detectedIssuesMutable.any { it.damageType == "Spalling" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Major Crack" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Algae" } -> "Moderate"
                detectedIssuesMutable.any { it.damageType == "Minor Crack" || it.damageType == "Paint Damage" } -> "Low"
                detectedIssuesMutable.isNotEmpty() -> "Low"
                plainConf > SHOW_THRESHOLD -> "None"
                else -> "Low"
            }

            model.close()
            originalBitmap.recycle()
            resizedBitmap.recycle()

            ImageAssessment(
                imageUri = imageUri,
                damageType = primaryDamageType,
                damageLevel = primaryDamageLevel,
                confidence = maxConfidence,
                firebaseImageUrl = "",
                detectedIssues = detectedIssuesMutable.toList(),
                imageRisk = imageRisk
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    LaunchedEffect(Unit) {
        if (buildingAreas.isNotEmpty() && !isSavedToFirebase) {
            isAnalyzing = true
            isSaving = true
            analysisError = null

            yield()
            delay(500)

            try {
                val areaAnalysesList = mutableListOf<AreaAnalysis>()

                buildingAreas.forEach { area ->
                    val imageAssessments = area.photos.mapNotNull { analyzeImageWithTensorFlow(it) }

                    val areaRisk = when {
                        imageAssessments.any { it.imageRisk == "High" } -> "High Risk"
                        imageAssessments.any { it.imageRisk == "Moderate" } -> "Moderate Risk"
                        imageAssessments.any { it.imageRisk == "Low" } -> "Low Risk"
                        else -> "Low Risk"
                    }

                    areaAnalysesList.add(
                        AreaAnalysis(
                            areaId = area.id,
                            areaName = area.name,
                            imageAssessments = imageAssessments,
                            areaRisk = areaRisk
                        )
                    )
                }

                val damageTypeMap = mutableMapOf<String, MutableList<Float>>()

                areaAnalysesList.forEach { areaAnalysis ->
                    areaAnalysis.imageAssessments.forEach { imageAssessment ->
                        imageAssessment.detectedIssues.forEach { issue ->
                            damageTypeMap.getOrPut(issue.damageType) { mutableListOf() }.add(issue.confidence)
                        }
                    }
                }

                val detectionSummary = damageTypeMap.map { (damageType, confidences) ->
                    DetectionSummaryItem(
                        damageType = damageType,
                        count = confidences.size,
                        avgConfidence = confidences.average().toFloat()
                    )
                }.sortedByDescending { it.count }

                val overallRisk = when {
                    areaAnalysesList.any { it.areaRisk == "High Risk" } -> "High Risk"
                    areaAnalysesList.any { it.areaRisk == "Moderate Risk" } -> "Moderate Risk"
                    areaAnalysesList.any { it.areaRisk == "Low Risk" } -> "Low Risk"
                    else -> "Low Risk"
                }

                val totalIssues = detectionSummary.sumOf { it.count }

                val summary = AssessmentSummary(
                    overallRisk = overallRisk,
                    totalIssues = totalIssues,
                    detectionSummary = detectionSummary,
                    areaAnalyses = areaAnalysesList
                )

                isAnalyzing = false

                withContext(Dispatchers.IO) {
                    val success = onSaveToFirebase(summary)
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        if (success) {
                            assessmentSummary = summary
                            isSavedToFirebase = true
                        } else {
                            analysisError = "Failed to save assessment. Check your connection."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AssessmentResults", "Analysis failed", e)
                analysisError = "Analysis failed: ${e.message}"
                isAnalyzing = false
                isSaving = false
            }
        }
    }

    if (isAnalyzing || isSaving) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isAnalyzing) "Analyzing images..." else "Saving to database...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }

    if (analysisError != null && !isAnalyzing && !isSaving) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = analysisError ?: "An error occurred",
                color = Color.Red,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(context, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }) {
                Text("Back to Dashboard")
            }
        }
        return
    }

    assessmentSummary?.let { summary ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                            (context as? ComponentActivity)?.finish()
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                    Text(
                        text = "Assessment Details",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = assessmentName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (summary.overallRisk) {
                            "High Risk" -> Color(0xFFFFEBEE)
                            "Moderate Risk" -> Color(0xFFFFF3E0)
                            else -> Color(0xFFE8F5E9)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        when (summary.overallRisk) {
                            "High Risk" -> Color(0xFFD32F2F)
                            "Moderate Risk" -> Color(0xFFF57C00)
                            else -> Color(0xFF388E3C)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when (summary.overallRisk) {
                                "High Risk" -> Color(0xFFD32F2F)
                                "Moderate Risk" -> Color(0xFFF57C00)
                                else -> Color(0xFF388E3C)
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = summary.overallRisk,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Based on analysis of ${summary.areaAnalyses.sumOf { it.imageAssessments.size }} photos across ${summary.areaAnalyses.size} areas",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ALWAYS show Building Information section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val hasBuildingInfo = buildingType.isNotEmpty() || constructionYear.isNotEmpty() ||
                                renovationYear.isNotEmpty() || floors.isNotEmpty() || material.isNotEmpty() ||
                                foundation.isNotEmpty() || environment.isNotEmpty() || previousIssues.isNotEmpty() ||
                                occupancy.isNotEmpty() || environmentalRisks.isNotEmpty() || notes.isNotEmpty()

                        if (!hasBuildingInfo) {
                            // Show friendly message when no building info provided
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "No info",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No Building Information Available",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF374151),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tap the edit icon above to add details about your structure.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF6B7280),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            // Show building info when available
                            if (buildingType.isNotEmpty() || floors.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    if (buildingType.isNotEmpty()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("TYPE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(6.dp))
                                            Text(buildingType, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    if (floors.isNotEmpty()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("FLOORS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(6.dp))
                                            Text(floors, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            if (material.isNotEmpty() || constructionYear.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    if (material.isNotEmpty()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("MATERIAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(6.dp))
                                            Text(material, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    if (constructionYear.isNotEmpty()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("BUILT", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(6.dp))
                                            Text(constructionYear, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            if (renovationYear.isNotEmpty()) {
                                BuildingInfoRow("Last Renovation", renovationYear.ifEmpty { "Never" })
                            }
                            if (foundation.isNotEmpty()) {
                                BuildingInfoRow("Foundation Type", foundation)
                            }
                            if (environment.isNotEmpty()) {
                                BuildingInfoRow("Environment", environment)
                            }
                            if (occupancy.isNotEmpty()) {
                                BuildingInfoRow("Occupancy Level", occupancy)
                            }
                            if (previousIssues.isNotEmpty()) {
                                BuildingInfoRow("Previous Issues", previousIssues.joinToString(", ").ifEmpty { "None" })
                            }
                            if (environmentalRisks.isNotEmpty()) {
                                BuildingInfoRow("Environmental Risk", environmentalRisks.firstOrNull() ?: "None")
                            }
                            if (notes.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 12.dp))
                                Text("Additional Notes", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text(notes, fontSize = 13.sp, color = Color.Black, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Detection Summary",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (summary.detectionSummary.isEmpty()) {
                            Text(
                                text = "No visible damage detected",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        } else {
                            summary.detectionSummary.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.damageType,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Avg Confidence: ${(item.avgConfidence * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF2196F3),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${item.count}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Area-by-Area Analysis",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(16.dp))

                summary.areaAnalyses.forEach { areaAnalysis ->
                    var areaExpanded by remember { mutableStateOf(true) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { areaExpanded = !areaExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = areaAnalysis.areaName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (areaAnalysis.areaRisk) {
                                            "High Risk" -> Color(0xFFFFEBEE)
                                            "Moderate Risk" -> Color(0xFFFFF3E0)
                                            else -> Color(0xFFE8F5E9)
                                        }
                                    ) {
                                        Text(
                                            text = areaAnalysis.areaRisk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (areaAnalysis.areaRisk) {
                                                "High Risk" -> Color(0xFFD32F2F)
                                                "Moderate Risk" -> Color(0xFFF57C00)
                                                else -> Color(0xFF388E3C)
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val rotation by animateFloatAsState(if (areaExpanded) 180f else 0f)
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.rotate(rotation)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = areaExpanded) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        text = "Image Analysis",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    areaAnalysis.imageAssessments.forEachIndexed { index, imageAssessment ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp)) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(imageAssessment.imageUri),
                                                    contentDescription = "Image ${index + 1}",
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .background(Color.Gray, RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Image ${index + 1}",
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = when (imageAssessment.imageRisk) {
                                                                "High" -> Color(0xFFFFEBEE)
                                                                "Moderate" -> Color(0xFFFFF3E0)
                                                                "Low" -> Color(0xFFE8F5E9)
                                                                else -> Color(0xFFE0E0E0)
                                                            }
                                                        ) {
                                                            Text(
                                                                text = when (imageAssessment.imageRisk) {
                                                                    "High" -> "HIGH"
                                                                    "Moderate" -> "MODERATE"
                                                                    "Low" -> "LOW"
                                                                    else -> "PLAIN"
                                                                },
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = when (imageAssessment.imageRisk) {
                                                                    "High" -> Color(0xFFD32F2F)
                                                                    "Moderate" -> Color(0xFFF57C00)
                                                                    "Low" -> Color(0xFF388E3C)
                                                                    else -> Color.Gray
                                                                },
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    if (imageAssessment.detectedIssues.isEmpty()) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = Color(0xFF388E3C),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Clean surface - No damage detected",
                                                                fontSize = 12.sp,
                                                                color = Color(0xFF388E3C)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Confidence: ${(imageAssessment.confidence * 100).toInt()}%",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    } else {
                                                        imageAssessment.detectedIssues.forEach { issue ->
                                                            Text(
                                                                text = "${issue.damageType}",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color.Black
                                                            )
                                                            Text(
                                                                text = "Confidence: ${(issue.confidence * 100).toInt()}%",
                                                                fontSize = 12.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Recommended Actions",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val uniqueIssues = areaAnalysis.imageAssessments
                                        .flatMap { it.detectedIssues }
                                        .groupBy { "${it.damageType}-${it.damageLevel}" }

                                    if (uniqueIssues.isEmpty()) {
                                        val rec = (context as AssessmentResultsActivity).getRecommendation("Plain", "None")
                                        RecommendationCard(rec, 0, 0f)  // Changed second parameter from 0 to 0f (Float)
                                    } else {
                                        uniqueIssues.forEach { (_, issuesList) ->
                                            val firstIssue = issuesList.first()
                                            val avgConfidence = issuesList.map { it.confidence }.average().toFloat()
                                            val locationCount = issuesList.size
                                            val rec = (context as AssessmentResultsActivity).getRecommendation(firstIssue.damageType, firstIssue.damageLevel)
                                            RecommendationCard(rec, locationCount, avgConfidence)
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Download feature coming soon", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Full Report (PDF)", color = Color.White, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        if (!hasReanalyzed) {
                            showReanalyzeDialog = true
                        }
                    },
                    enabled = !hasReanalyzed,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (hasReanalyzed) Color.Gray else Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasReanalyzed) "Re-analysis Used" else "Re-analyze",
                        color = if (hasReanalyzed) Color.Gray else Color(0xFF2196F3),
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Back to History", color = Color(0xFF2196F3), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showReanalyzeDialog) {
            AlertDialog(
                onDismissRequest = { showReanalyzeDialog = false },
                title = {
                    Text(
                        "Re-analyze Images?",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("This will:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Re-run AI analysis on all ${summary.areaAnalyses.sumOf { it.imageAssessments.size }} images", fontSize = 13.sp)
                        Text("• Generate new detection results", fontSize = 13.sp)
                        Text("• Update the saved assessment", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Previous results will be overwritten.", fontSize = 13.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            hasReanalyzed = true
                            showReanalyzeDialog = false
                            coroutineScope.launch {
                                isAnalyzing = true

                                val areaAnalysesList = mutableListOf<AreaAnalysis>()

                                buildingAreas.forEach { area ->
                                    val imageAssessments = area.photos.mapNotNull { analyzeImageWithTensorFlow(it) }

                                    val areaRisk = when {
                                        imageAssessments.any { it.imageRisk == "High" } -> "High Risk"
                                        imageAssessments.any { it.imageRisk == "Moderate" } -> "Moderate Risk"
                                        imageAssessments.any { it.imageRisk == "Low" } -> "Low Risk"
                                        else -> "Low Risk"
                                    }

                                    areaAnalysesList.add(
                                        AreaAnalysis(
                                            areaId = area.id,
                                            areaName = area.name,
                                            imageAssessments = imageAssessments,
                                            areaRisk = areaRisk
                                        )
                                    )
                                }

                                val damageTypeMap = mutableMapOf<String, MutableList<Float>>()

                                areaAnalysesList.forEach { areaAnalysis ->
                                    areaAnalysis.imageAssessments.forEach { imageAssessment ->
                                        imageAssessment.detectedIssues.forEach { issue ->
                                            damageTypeMap.getOrPut(issue.damageType) { mutableListOf() }.add(issue.confidence)
                                        }
                                    }
                                }

                                val detectionSummary = damageTypeMap.map { (damageType, confidences) ->
                                    DetectionSummaryItem(
                                        damageType = damageType,
                                        count = confidences.size,
                                        avgConfidence = confidences.average().toFloat()
                                    )
                                }.sortedByDescending { it.count }

                                val newRisk = when {
                                    areaAnalysesList.any { it.areaRisk == "High Risk" } -> "High Risk"
                                    areaAnalysesList.any { it.areaRisk == "Moderate Risk" } -> "Moderate Risk"
                                    else -> "Low Risk"
                                }

                                val totalIssues = detectionSummary.sumOf { it.count }

                                val newSummary = AssessmentSummary(
                                    overallRisk = newRisk,
                                    totalIssues = totalIssues,
                                    detectionSummary = detectionSummary,
                                    areaAnalyses = areaAnalysesList
                                )

                                isAnalyzing = false
                                isSaving = true

                                val success = withContext(Dispatchers.IO) {
                                    (context as AssessmentResultsActivity).saveAssessmentToFirebase(
                                        assessmentName, newSummary, buildingType, constructionYear,
                                        renovationYear, floors, material, foundation, environment,
                                        previousIssues, occupancy, environmentalRisks, notes,
                                        isReanalysis = true
                                    )
                                }

                                isSaving = false

                                if (success) {
                                    assessmentSummary = newSummary
                                    Toast.makeText(context, "✓ Re-analysis complete! Results updated.", Toast.LENGTH_LONG).show()
                                } else {
                                    analysisError = "Failed to save re-analyzed results"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                    ) {
                        Text("Re-analyze Now")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showReanalyzeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun BuildingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun RecommendationCard(recommendation: DamageRecommendation, locationCount: Int, avgConfidence: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = recommendation.severityBgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = recommendation.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = recommendation.severityColor
                ) {
                    Text(
                        text = recommendation.severity,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (locationCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$locationCount image${if (locationCount > 1) "s" else ""} detected • AI Confidence: ${(avgConfidence * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = recommendation.description,
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            recommendation.actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = recommendation.severityColor,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action,
                        fontSize = 12.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}