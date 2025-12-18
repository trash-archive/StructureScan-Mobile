package com.example.structurescan

import android.content.Context
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val imageRisk: String = "Low",
    val structuralTilt: StructuralTiltAnalyzer.StructuralTiltResult? = null
)

data class AreaAnalysis(
    val areaId: String,
    val areaName: String,
    val areaType: AreaType,
    val imageAssessments: List<ImageAssessment>,
    val areaRisk: String,
    val structuralAnalysisEnabled: Boolean = false
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
    private var originalBuildingAreas: ArrayList<BuildingArea>? = null

    // NEW: Store if re-analysis has been used
    private val sharedPrefs by lazy {
        getSharedPreferences("assessment_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        currentAssessmentId = intent.getStringExtra("ASSESSMENT_ID")

        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"
        val buildingAreas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, BuildingArea::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS)
        } ?: arrayListOf()

        originalBuildingAreas = buildingAreas

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

        // NEW: Check if this assessment has used re-analysis
        val hasUsedReanalysis = currentAssessmentId?.let {
            sharedPrefs.getBoolean("reanalyzed_$it", false)
        } ?: false

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
                    assessmentId = currentAssessmentId, // NEW: Pass assessment ID
                    initialHasReanalyzed = hasUsedReanalysis, // NEW: Pass re-analysis state
                    onSaveToFirebase = { summary, isReanalysis ->
                        saveAssessmentToFirebase(
                            assessmentName, summary, buildingType, constructionYear,
                            renovationYear, floors, material, foundation, environment,
                            previousIssues, occupancy, environmentalRisks, notes, isReanalysis
                        )
                    },
                    onMarkReanalyzed = { // NEW: Callback to save re-analysis state
                        currentAssessmentId?.let { id ->
                            sharedPrefs.edit().putBoolean("reanalyzed_$id", true).apply()
                        }
                    }
                )
            }
        }
    }

    // FIXED: onActivityResult now only updates state variables, doesn't recreate
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val wasUpdated = data.getBooleanExtra("UPDATED", false)
            if (wasUpdated) {
                // Simply update the intent extras so if the user rotates screen,
                // the updated data persists
                intent.putExtra("assessmentName", data.getStringExtra("assessmentName"))
                intent.putExtra(IntentKeys.BUILDING_TYPE, data.getStringExtra(IntentKeys.BUILDING_TYPE))
                intent.putExtra(IntentKeys.CONSTRUCTION_YEAR, data.getStringExtra(IntentKeys.CONSTRUCTION_YEAR))
                intent.putExtra(IntentKeys.RENOVATION_YEAR, data.getStringExtra(IntentKeys.RENOVATION_YEAR))
                intent.putExtra(IntentKeys.FLOORS, data.getStringExtra(IntentKeys.FLOORS))
                intent.putExtra(IntentKeys.MATERIAL, data.getStringExtra(IntentKeys.MATERIAL))
                intent.putExtra(IntentKeys.FOUNDATION, data.getStringExtra(IntentKeys.FOUNDATION))
                intent.putExtra(IntentKeys.ENVIRONMENT, data.getStringExtra(IntentKeys.ENVIRONMENT))
                intent.putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, data.getStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES))
                intent.putExtra(IntentKeys.OCCUPANCY, data.getStringExtra(IntentKeys.OCCUPANCY))
                intent.putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, data.getStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS))
                intent.putExtra(IntentKeys.NOTES, data.getStringExtra(IntentKeys.NOTES))
            }
        }
    }

    private suspend fun uploadImageToStorage(
        imageUri: Uri,
        userId: String,
        assessmentId: String,
        areaId: String,
        imageIndex: Int
    ): String? {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
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
        if (currentUser == null) return false
        val userId = currentUser.uid

        return try {
            // Check if this is a re-analysis
            val isActualReanalysis = intent.getBooleanExtra("IS_REANALYSIS", false)

            val assessmentId = if (isActualReanalysis && currentAssessmentId != null) {
                currentAssessmentId!!
            } else {
                UUID.randomUUID().toString().also { currentAssessmentId = it }
            }

            val areasData = mutableListOf<HashMap<String, Any>>()

            // Use runBlocking with proper Dispatchers.IO context
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                summary.areaAnalyses.forEach { areaAnalysis ->
                    val imagesData = mutableListOf<HashMap<String, Any>>()

                    areaAnalysis.imageAssessments.forEachIndexed { index, assessment ->
                        val firebaseImageUrl = if (isActualReanalysis && assessment.firebaseImageUrl.isNotEmpty()) {
                            assessment.firebaseImageUrl
                        } else {
                            uploadImageToStorage(
                                assessment.imageUri,
                                userId,
                                assessmentId,
                                areaAnalysis.areaId,
                                index
                            ) ?: throw Exception("Failed to upload image ${index + 1}")
                        }

                        val recommendationsForImage = if (assessment.detectedIssues.isEmpty()) {
                            listOf(
                                hashMapOf(
                                    "title" to "Clean Surface",
                                    "description" to "No structural damage or surface deterioration detected.",
                                    "severity" to "GOOD",
                                    "actions" to listOf(
                                        "Continue regular maintenance schedule",
                                        "Monitor during routine inspections",
                                        "No immediate action required"
                                    )
                                )
                            )
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

                        val imageData = hashMapOf(
                            "damageType" to assessment.damageType,
                            "damageLevel" to assessment.damageLevel,
                            "confidence" to assessment.confidence,
                            "imageUri" to firebaseImageUrl,
                            "localImageUri" to assessment.imageUri.toString(),
                            "detectedIssues" to assessment.detectedIssues.map {
                                mapOf(
                                    "type" to it.damageType,
                                    "level" to it.damageLevel,
                                    "confidence" to it.confidence
                                )
                            },
                            "imageRisk" to assessment.imageRisk,
                            "recommendations" to recommendationsForImage
                        )

                        assessment.structuralTilt?.let { tilt ->
                            imageData["structuralVerticalTilt"] = tilt.averageVerticalTilt
                            imageData["structuralHorizontalTilt"] = tilt.averageHorizontalTilt
                            imageData["structuralTiltSeverity"] = tilt.tiltSeverity.name
                            imageData["structuralTiltConfidence"] = tilt.confidence
                            imageData["structuralLinesDetected"] = tilt.detectedLines
                            imageData["structuralTiltWarning"] = tilt.warning ?: ""
                        }

                        imagesData.add(imageData)
                    }

                    areasData.add(
                        hashMapOf(
                            "areaId" to areaAnalysis.areaId,
                            "areaName" to areaAnalysis.areaName,
                            "areaType" to areaAnalysis.areaType.name,
                            "areaRisk" to areaAnalysis.areaRisk,
                            "structuralAnalysisEnabled" to areaAnalysis.structuralAnalysisEnabled,
                            "images" to imagesData
                        )
                    )
                }
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

            // Save to Firestore
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (isActualReanalysis) {
                    // Get current reanalysisCount and increment it
                    val docRef = firestore.collection("users")
                        .document(userId)
                        .collection("assessments")
                        .document(assessmentId)

                    val currentDoc = docRef.get().await()
                    val currentReanalysisCount = currentDoc.getLong("reanalysisCount")?.toInt() ?: 0

                    // Add incremented count to assessment data
                    assessmentData["reanalysisCount"] = currentReanalysisCount + 1
                    assessmentData["lastReanalysisDate"] = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())

                    docRef.update(assessmentData as Map<String, Any>).await()
                } else {
                    // New assessment: Initialize reanalysisCount to 0
                    assessmentData["reanalysisCount"] = 0

                    firestore.collection("users")
                        .document(userId)
                        .collection("assessments")
                        .document(assessmentId)
                        .set(assessmentData)
                        .await()
                }
            }

            true
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Upload failed", e)
            false
        }
    }

    // Add this function in AssessmentResultsActivity
    suspend fun getReanalysisCount(assessmentId: String): Int {
        return try {
            val userId = firebaseAuth.currentUser?.uid ?: return 0
            val doc = firestore.collection("users")
                .document(userId)
                .collection("assessments")
                .document(assessmentId)
                .get()
                .await()

            doc.getLong("reanalysisCount")?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e("ReanalysisCount", "Failed to get count", e)
            0
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
                    "Professional will remove damaged concrete, clean metal bars, fill with repair cement",
                    "After repair, seal the surface to protect it from water and prevent future damage"
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
                "Small Hairline Cracks",
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
                    "Find and fix the water problem FIRST (look for leaks, bad drainage, or too much humidity)",
                    "Proper fix steps: scrape off loose paint, clean the surface, apply primer, then paint",
                    "Choose the right paint - mildew-resistant for bathrooms/kitchens, weather-resistant for outside",
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
                    "Find and fix why it's wet (improve drainage, fix gutters, repair any roof leaks)",
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
    assessmentId: String? = null, // NEW: Receive assessment ID
    initialHasReanalyzed: Boolean = false, // NEW: Receive re-analysis state
    onSaveToFirebase: (AssessmentSummary, Boolean) -> Boolean = { _, _ -> false },
    onReanalyze: () -> Unit = {},
    onMarkReanalyzed: () -> Unit = {} // NEW: Callback to mark re-analysis used
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? AssessmentResultsActivity

    var currentBuildingType by remember { mutableStateOf(buildingType) }
    var currentConstructionYear by remember { mutableStateOf(constructionYear) }
    var currentRenovationYear by remember { mutableStateOf(renovationYear) }
    var currentFloors by remember { mutableStateOf(floors) }
    var currentMaterial by remember { mutableStateOf(material) }
    var currentFoundation by remember { mutableStateOf(foundation) }
    var currentEnvironment by remember { mutableStateOf(environment) }
    var currentPreviousIssues by remember { mutableStateOf(previousIssues) }
    var currentOccupancy by remember { mutableStateOf(occupancy) }
    var currentEnvironmentalRisks by remember { mutableStateOf(environmentalRisks) }
    var currentNotes by remember { mutableStateOf(notes) }
    var currentAssessmentName by remember { mutableStateOf(assessmentName) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isReanalyzing by remember { mutableStateOf(false) }
    var assessmentSummary by remember { mutableStateOf<AssessmentSummary?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var isSavedToFirebase by remember { mutableStateOf(false) }
    var showReanalyzeDialog by remember { mutableStateOf(false) }
    var hasReanalyzed by remember { mutableStateOf(initialHasReanalyzed) } // FIXED: Use initial value
    var loadingMessage by remember { mutableStateOf("Analyzing images, please wait...") }

    val SHOW_THRESHOLD = 0.50f

    // NEW: Update building info when intent changes
    LaunchedEffect(buildingType, constructionYear, renovationYear, floors, material, foundation, environment, previousIssues, occupancy, environmentalRisks, notes, assessmentName) {
        currentBuildingType = buildingType
        currentConstructionYear = constructionYear
        currentRenovationYear = renovationYear
        currentFloors = floors
        currentMaterial = material
        currentFoundation = foundation
        currentEnvironment = environment
        currentPreviousIssues = previousIssues
        currentOccupancy = occupancy
        currentEnvironmentalRisks = environmentalRisks
        currentNotes = notes
        currentAssessmentName = assessmentName
    }

    fun launchEditBuildingInfo() {
        val intent = Intent(context, EditBuildingInfoActivity::class.java).apply {
            putExtra("assessmentName", currentAssessmentName)
            putExtra(IntentKeys.BUILDING_TYPE, currentBuildingType)
            putExtra(IntentKeys.CONSTRUCTION_YEAR, currentConstructionYear)
            putExtra(IntentKeys.RENOVATION_YEAR, currentRenovationYear)
            putExtra(IntentKeys.FLOORS, currentFloors)
            putExtra(IntentKeys.MATERIAL, currentMaterial)
            putExtra(IntentKeys.FOUNDATION, currentFoundation)
            putExtra(IntentKeys.ENVIRONMENT, currentEnvironment)
            putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, ArrayList(currentPreviousIssues))
            putExtra(IntentKeys.OCCUPANCY, currentOccupancy)
            putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, ArrayList(currentEnvironmentalRisks))
            putExtra(IntentKeys.NOTES, currentNotes)
            putExtra("ASSESSMENT_ID", assessmentId) // NEW: Pass assessment ID
        }
        (context as? ComponentActivity)?.startActivityForResult(intent, 100)
    }

    BackHandler(enabled = true) {
        if (isSaving || isReanalyzing) {
            // Block back button during saving or re-analyzing
        } else {
            val intent = Intent(context, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
    }

    // FIXED: Simplified analyzeImageWithTensorFlow - returns assessment without Firebase URL
    fun analyzeImageWithTensorFlow(
        imageUri: Uri,
        cameraTilt: TiltMeasurement? = null,
        enableStructuralAnalysis: Boolean = false
    ): ImageAssessment? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) throw Exception("Failed to load image")

            val structuralTilt = if (enableStructuralAnalysis) {
                val analyzer = StructuralTiltAnalyzer()
                analyzer.analyzeWithCompensation(originalBitmap, cameraTilt)
            } else null

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
            if (crackHigh >= SHOW_THRESHOLD) detectedIssuesMutable.add(DetectedIssue("Spalling", "High", crackHigh))
            if (crackMod >= SHOW_THRESHOLD) detectedIssuesMutable.add(DetectedIssue("Major Crack", "High", crackMod))
            if (crackLow >= SHOW_THRESHOLD) detectedIssuesMutable.add(DetectedIssue("Minor Crack", "Low", crackLow))
            if (paintConf >= SHOW_THRESHOLD) detectedIssuesMutable.add(DetectedIssue("Paint Damage", "Low", paintConf))
            if (algaeConf >= SHOW_THRESHOLD) detectedIssuesMutable.add(DetectedIssue("Algae", "Moderate", algaeConf))

            val imageRisk = when {
                detectedIssuesMutable.any { it.damageType == "Spalling" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Major Crack" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Algae" } -> "Moderate"
                detectedIssuesMutable.any { it.damageType == "Minor Crack" || it.damageType == "Paint Damage" } -> "Low"
                detectedIssuesMutable.isNotEmpty() -> "Low"
                plainConf >= SHOW_THRESHOLD -> "None"
                else -> "Low"
            }

            model.close()
            originalBitmap.recycle()
            resizedBitmap.recycle()

            // Return without Firebase URL - will be added by caller
            ImageAssessment(
                imageUri = imageUri,
                damageType = primaryDamageType,
                damageLevel = primaryDamageLevel,
                confidence = maxConfidence,
                firebaseImageUrl = "", // Empty initially
                detectedIssues = detectedIssuesMutable.toList(),
                imageRisk = imageRisk,
                structuralTilt = structuralTilt
            )
        } catch (e: Exception) {
            Log.e("ImageAnalysis", "Analysis failed", e)
            null
        }
    }

    // FIXED: In-place re-analysis with proper Firebase URL preservation
    suspend fun performInPlaceReanalysis() {
        isReanalyzing = true
        isAnalyzing = true
        loadingMessage = "Re-analyzing images, please wait..."

        val existingUrls = mutableMapOf<Uri, String>()
        assessmentSummary?.areaAnalyses?.forEach { area ->
            area.imageAssessments.forEach { assessment ->
                if (assessment.firebaseImageUrl.isNotEmpty()) {
                    existingUrls[assessment.imageUri] = assessment.firebaseImageUrl
                }
            }
        }

        try {
            val areaAnalysesList = mutableListOf<AreaAnalysis>()

            buildingAreas.forEach { area ->
                val imageAssessments = area.photos.mapIndexed { index, uri ->
                    val cameraTilt = area.photoTilts.getOrNull(index)
                    val newAssessment = analyzeImageWithTensorFlow(
                        imageUri = uri,
                        cameraTilt = cameraTilt,
                        enableStructuralAnalysis = area.requiresStructuralTilt
                    )

                    newAssessment?.copy(
                        firebaseImageUrl = existingUrls[uri] ?: ""
                    )
                }.filterNotNull()

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
                        areaType = area.areaType,
                        imageAssessments = imageAssessments,
                        areaRisk = areaRisk,
                        structuralAnalysisEnabled = area.requiresStructuralTilt
                    )
                )
            }

            val damageTypeMap = mutableMapOf<String, MutableList<Float>>()
            areaAnalysesList.forEach { areaAnalysis ->
                areaAnalysis.imageAssessments.forEach { imageAssessment ->
                    imageAssessment.detectedIssues.forEach { issue ->
                        damageTypeMap.getOrPut(issue.damageType) { mutableListOf() }
                            .add(issue.confidence)
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
            isSaving = true
            loadingMessage = "Saving updated analysis..."

            withContext(Dispatchers.IO) {
                val success = onSaveToFirebase(summary, true)

                withContext(Dispatchers.Main) {
                    isSaving = false
                    isReanalyzing = false

                    if (success) {
                        assessmentSummary = summary
                        hasReanalyzed = true
                        onMarkReanalyzed() // NEW: Save to SharedPreferences
                        Toast.makeText(context, "Re-analysis completed successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        analysisError = "Failed to save re-analysis. Check your connection."
                        Toast.makeText(context, "Failed to save re-analysis", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AssessmentResults", "Re-analysis failed", e)
            analysisError = "Re-analysis failed: ${e.message}"
            isAnalyzing = false
            isSaving = false
            isReanalyzing = false
            Toast.makeText(context, "Re-analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (buildingAreas.isNotEmpty() && !isSavedToFirebase && assessmentId == null) {            isAnalyzing = true
            // Only analyze if this is a NEW assessment (no ID yet)
            isAnalyzing = true
            isSaving = true
            analysisError = null
            loadingMessage = "Analyzing images, please wait..."

            yield()
            delay(500)

            try {
                val areaAnalysesList = mutableListOf<AreaAnalysis>()

                buildingAreas.forEach { area ->
                    val imageAssessments = area.photos.mapIndexed { index, uri ->
                        val cameraTilt = area.photoTilts.getOrNull(index)
                        analyzeImageWithTensorFlow(
                            imageUri = uri,
                            cameraTilt = cameraTilt,
                            enableStructuralAnalysis = area.requiresStructuralTilt
                        )
                    }.filterNotNull()

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
                            areaType = area.areaType,
                            imageAssessments = imageAssessments,
                            areaRisk = areaRisk,
                            structuralAnalysisEnabled = area.requiresStructuralTilt
                        )
                    )
                }

                val damageTypeMap = mutableMapOf<String, MutableList<Float>>()
                areaAnalysesList.forEach { areaAnalysis ->
                    areaAnalysis.imageAssessments.forEach { imageAssessment ->
                        imageAssessment.detectedIssues.forEach { issue ->
                            damageTypeMap.getOrPut(issue.damageType) { mutableListOf() }
                                .add(issue.confidence)
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
                loadingMessage = "Saving assessment..."

                withContext(Dispatchers.IO) {
                    val success = onSaveToFirebase(summary, false)

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
        } else if (assessmentId != null) {
            // Assessment already exists - just mark as saved
            isSavedToFirebase = true
        }
    }

    // Re-analyze Dialog
    if (showReanalyzeDialog) {
        Dialog(onDismissRequest = { showReanalyzeDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Re-analyze Images?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "This will re-analyze all images with fresh AI processing. The updated results will replace the current assessment data. You can only do this once.",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showReanalyzeDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF6B7280)
                            )
                        ) {
                            Text(
                                "Cancel",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                showReanalyzeDialog = false
                                coroutineScope.launch {
                                    performInPlaceReanalysis()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Re-analyze",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Enhanced Loading Dialog with better icon
    if (isAnalyzing || isSaving || isReanalyzing) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.padding(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated icon with rotation
                    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )

                    Box(contentAlignment = Alignment.Center) {
                        // Outer rotating circle
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp),
                            color = Color(0xFF2196F3),
                            strokeWidth = 5.dp
                        )

                        // Inner icon with pulse effect
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Icon(
                            imageVector = if (isReanalyzing) Icons.Default.Refresh else Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    rotationZ = if (isReanalyzing) rotation else 0f
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isReanalyzing) "Re-analyzing images..." else if (isSaving) "Saving results..." else "Analyzing images...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated loading bar
                    val progressTransition = rememberInfiniteTransition(label = "progress")
                    val progress by progressTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "progress"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE3F2FD))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF2196F3),
                                            Color(0xFF1976D2),
                                            Color(0xFF0D47A1)
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isReanalyzing) "Processing with fresh AI analysis..." else "This may take a moment...",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (analysisError != null && !isAnalyzing && !isSaving && !isReanalyzing) {
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
            // Top Bar
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

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Assessment Name
                Text(
                    text = currentAssessmentName,
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

                // Overall Risk Card
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
                            text = "Based on analysis of ${summary.areaAnalyses.sumOf { it.imageAssessments.size }} photos across ${summary.areaAnalyses.size} areas.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Structural Tilt Summary
                if (summary.areaAnalyses.any { it.structuralAnalysisEnabled }) {
                    StructuralTiltSummaryCard(summary = summary)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Building Information Card
                BuildingInformationCard(
                    buildingType = currentBuildingType,
                    constructionYear = currentConstructionYear,
                    renovationYear = currentRenovationYear,
                    floors = currentFloors,
                    material = currentMaterial,
                    foundation = currentFoundation,
                    environment = currentEnvironment,
                    occupancy = currentOccupancy,
                    previousIssues = currentPreviousIssues,
                    environmentalRisks = currentEnvironmentalRisks,
                    notes = currentNotes,
                    assessmentName = currentAssessmentName,
                    onEditClick = { launchEditBuildingInfo() },
                    initiallyExpanded = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Detection Summary
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

                // Area-by-Area Analysis
                Text(
                    text = "Area-by-Area Analysis",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Area Analysis Cards
                summary.areaAnalyses.forEach { areaAnalysis ->
                    var areaExpanded by remember { mutableStateOf(true) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
                    ) {
                        Column {
                            // Header
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = when (areaAnalysis.areaRisk) {
                                    "High Risk" -> Color(0xFFFFEBEE)
                                    "Moderate Risk" -> Color(0xFFFFF3E0)
                                    else -> Color(0xFFF0FDF4)
                                },
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { areaExpanded = !areaExpanded }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.7f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = null,
                                                    tint = Color(0xFF8B5CF6),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = areaAnalysis.areaName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF333333)
                                            )
                                            Text(
                                                text = "${areaAnalysis.imageAssessments.size} photo${if (areaAnalysis.imageAssessments.size != 1) "s" else ""} analyzed",
                                                fontSize = 12.sp,
                                                color = Color(0xFF666666)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when (areaAnalysis.areaRisk) {
                                                "High Risk" -> Color(0xFFDC2626).copy(alpha = 0.15f)
                                                "Moderate Risk" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                else -> Color(0xFF16A34A).copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Text(
                                                text = areaAnalysis.areaRisk,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = when (areaAnalysis.areaRisk) {
                                                    "High Risk" -> Color(0xFFDC2626)
                                                    "Moderate Risk" -> Color(0xFFF59E0B)
                                                    else -> Color(0xFF059669)
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }

                                        val rotation by animateFloatAsState(
                                            targetValue = if (areaExpanded) 0f else -90f,
                                            label = "toggle"
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (areaExpanded) "Collapse" else "Expand",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(rotation),
                                            tint = Color(0xFF666666)
                                        )
                                    }
                                }
                            }

                            // Content
                            AnimatedVisibility(visible = areaExpanded) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (areaAnalysis.structuralAnalysisEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFEFF6FF),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Architecture,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Structural tilt analysis enabled for this area",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1E40AF)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                    }

                                    Text(
                                        text = "Image Analysis",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    areaAnalysis.imageAssessments.forEachIndexed { index, imageAssessment ->
                                        ImageAnalysisCard(
                                            imageNumber = index + 1,
                                            assessment = imageAssessment
                                        )
                                        if (index < areaAnalysis.imageAssessments.size - 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "💡", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Recommended Actions",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF333333)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val uniqueIssues = areaAnalysis.imageAssessments
                                        .flatMap { it.detectedIssues }
                                        .groupBy { "${it.damageType}-${it.damageLevel}" }

                                    if (uniqueIssues.isEmpty()) {
                                        val rec = (context as AssessmentResultsActivity).getRecommendation("Plain", "None")
                                        RecommendationCard(rec, 0, 0f)
                                    } else {
                                        uniqueIssues.forEach { (_, issuesList) ->
                                            val firstIssue = issuesList.first()
                                            val avgConfidence = issuesList.map { it.confidence }.average().toFloat()
                                            val locationCount = issuesList.size
                                            val rec = (context as AssessmentResultsActivity).getRecommendation(
                                                firstIssue.damageType,
                                                firstIssue.damageLevel
                                            )
                                            RecommendationCard(rec, locationCount, avgConfidence)
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action Buttons - UPDATED
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Re-analyze Button - Disabled after use
                        Button(
                            onClick = {
                                if (!hasReanalyzed) {
                                    showReanalyzeDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !hasReanalyzed && !isReanalyzing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3),
                                disabledContainerColor = Color(0xFFBDBDBD)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (hasReanalyzed) Color.White.copy(alpha = 0.6f) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasReanalyzed) "Already Re-analyzed" else "Re-analyze Images",
                                color = if (hasReanalyzed) Color.White.copy(alpha = 0.6f) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (hasReanalyzed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Re-analysis completed - button disabled",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Download Button
                        Button(
                            onClick = {
                                Toast.makeText(context, "Download feature coming soon", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Download Full Report (PDF)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Back to Dashboard Button
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, DashboardActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                                (context as? ComponentActivity)?.finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(2.dp, Color(0xFF2196F3)),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Back to Dashboard",
                                color = Color(0xFF2196F3),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// UI COMPONENTS

@Composable
fun BuildingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
fun RecommendationCard(
    recommendation: DamageRecommendation,
    locationCount: Int,
    avgConfidence: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    if (locationCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$locationCount image${if (locationCount > 1) "s" else ""} detected • AI Confidence: ${(avgConfidence * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (recommendation.severity) {
                        "HIGH" -> Color(0xFFFFEEEE)
                        "MODERATE" -> Color(0xFFFFF3E0)
                        "LOW" -> Color(0xFFF0FDF4)
                        else -> Color(0xFFF0FDF4)
                    }
                ) {
                    Text(
                        text = recommendation.severity,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (recommendation.severity) {
                            "HIGH" -> Color(0xFFDC2626)
                            "MODERATE" -> Color(0xFFF59E0B)
                            "LOW" -> Color(0xFF16A34A)
                            else -> Color(0xFF16A34A)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF9F9F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = recommendation.description,
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            recommendation.actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (index < recommendation.actions.size - 1) 10.dp else 0.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = action,
                        fontSize = 13.sp,
                        color = Color(0xFF333333),
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f).padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StructuralTiltSummaryCard(summary: AssessmentSummary) {
    val allAssessments = summary.areaAnalyses
        .filter { it.structuralAnalysisEnabled }
        .flatMap { it.imageAssessments }

    if (allAssessments.isEmpty()) return

    val severeCount = allAssessments.count { it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.SEVERE }
    val moderateCount = allAssessments.count { it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.MODERATE }
    val minorCount = allAssessments.count { it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.MINOR }
    val totalImages = allAssessments.size
    val hasStructuralIssues = (severeCount + moderateCount + minorCount) > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Architecture,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Structural Tilt Analysis",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFEF3C7)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Preliminary screening (±1-2° accuracy). Professional surveying recommended for critical concerns.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasStructuralIssues) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No significant structural tilt detected",
                        fontSize = 14.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    "Detected issues in ${severeCount + moderateCount + minorCount} of $totalImages analyzed images",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (severeCount > 0) {
                    StructuralQualityRow(
                        label = "Severe (5-10°+ building lean)",
                        count = severeCount,
                        percent = (severeCount * 100f / totalImages).toInt(),
                        color = Color(0xFFEF4444)
                    )
                }

                if (moderateCount > 0) {
                    StructuralQualityRow(
                        label = "Moderate (2-5°)",
                        count = moderateCount,
                        percent = (moderateCount * 100f / totalImages).toInt(),
                        color = Color(0xFFF59E0B)
                    )
                }

                if (minorCount > 0) {
                    StructuralQualityRow(
                        label = "Minor (<2°)",
                        count = minorCount,
                        percent = (minorCount * 100f / totalImages).toInt(),
                        color = Color(0xFF3B82F6)
                    )
                }
            }
        }
    }
}

@Composable
fun StructuralQualityRow(label: String, count: Int, percent: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, color = Color(0xFF374151))
        }
        Text(text = "$count ($percent%)", fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
fun ImageAnalysisCard(imageNumber: Int, assessment: ImageAssessment) {
    var showFullImage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Image(
                painter = rememberAsyncImagePainter(assessment.imageUri),
                contentDescription = "Image $imageNumber",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray)
                    .clickable { showFullImage = true },
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
                        text = "Image $imageNumber",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (assessment.imageRisk) {
                            "High" -> Color(0xFFFFEEEE)
                            "Moderate" -> Color(0xFFFFF3E0)
                            "Low" -> Color(0xFFF0FDF4)
                            else -> Color(0xFFF0FDF4)
                        }
                    ) {
                        Text(
                            text = when (assessment.imageRisk) {
                                "High" -> "HIGH"
                                "Moderate" -> "MODERATE"
                                "Low" -> "LOW"
                                else -> "PLAIN"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (assessment.imageRisk) {
                                "High" -> Color(0xFFDC2626)
                                "Moderate" -> Color(0xFFF59E0B)
                                "Low" -> Color(0xFF16A34A)
                                else -> Color(0xFF16A34A)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                assessment.structuralTilt?.let { structural ->
                    if (structural.tiltSeverity != StructuralTiltAnalyzer.TiltSeverity.NONE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Architecture,
                                contentDescription = "Structural Tilt",
                                tint = when (structural.tiltSeverity) {
                                    StructuralTiltAnalyzer.TiltSeverity.SEVERE -> Color(0xFFEF4444)
                                    StructuralTiltAnalyzer.TiltSeverity.MODERATE -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Tilt: ${structural.tiltSeverity.name} ${structural.averageVerticalTilt.toInt()}°",
                                fontSize = 12.sp,
                                color = when (structural.tiltSeverity) {
                                    StructuralTiltAnalyzer.TiltSeverity.SEVERE -> Color(0xFFEF4444)
                                    StructuralTiltAnalyzer.TiltSeverity.MODERATE -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (assessment.detectedIssues.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clean surface - No damage detected",
                            fontSize = 13.sp,
                            color = Color(0xFF16A34A)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Confidence: ${(assessment.confidence * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                } else {
                    assessment.detectedIssues.forEach { issue ->
                        Text(
                            text = "${issue.damageType}",
                            fontSize = 13.sp,
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

    if (showFullImage) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { showFullImage = false },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Image(
                        painter = rememberAsyncImagePainter(assessment.imageUri),
                        contentDescription = "Full image $imageNumber",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Image $imageNumber - Tap anywhere to close",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun BuildingInformationCard(
    buildingType: String,
    constructionYear: String,
    renovationYear: String,
    floors: String,
    material: String,
    foundation: String,
    environment: String,
    occupancy: String,
    previousIssues: List<String>,
    environmentalRisks: List<String>,
    notes: String,
    assessmentName: String,
    onEditClick: () -> Unit,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Building Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { onEditClick() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotation),
                        tint = Color.Gray
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val hasBuildingInfo = buildingType.isNotEmpty() || constructionYear.isNotEmpty() ||
                            renovationYear.isNotEmpty() || floors.isNotEmpty() || material.isNotEmpty() ||
                            foundation.isNotEmpty() || environment.isNotEmpty() || previousIssues.isNotEmpty() ||
                            occupancy.isNotEmpty() || environmentalRisks.isNotEmpty() || notes.isNotEmpty()

                    if (!hasBuildingInfo) {
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Building Information Available",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF374151),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        if (buildingType.isNotEmpty() || floors.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                if (buildingType.isNotEmpty()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("TYPE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(buildingType, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (floors.isNotEmpty()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("FLOORS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(6.dp))
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MATERIAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(material, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (constructionYear.isNotEmpty()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("BUILT", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(constructionYear, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        if (renovationYear.isNotEmpty()) BuildingInfoRow("Last Renovation", renovationYear)
                        if (foundation.isNotEmpty()) BuildingInfoRow("Foundation Type", foundation)
                        if (environment.isNotEmpty()) BuildingInfoRow("Environment", environment)
                        if (occupancy.isNotEmpty()) BuildingInfoRow("Occupancy Level", occupancy)
                        if (previousIssues.isNotEmpty()) BuildingInfoRow("Previous Issues", previousIssues.joinToString(", "))
                        if (environmentalRisks.isNotEmpty()) BuildingInfoRow("Environmental Risk", environmentalRisks.firstOrNull() ?: "None")

                        if (notes.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 12.dp))
                            Text("Additional Notes", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(notes, fontSize = 13.sp, color = Color.Black, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
