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
import com.example.structurescan.Utils.AreaSummary
import com.example.structurescan.Utils.ImageDetail
import com.example.structurescan.Utils.PdfReportGenerator
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.compose.rememberAsyncImagePainter
import com.example.structurescan.Utils.PdfAssessmentData
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
    val structuralTilt: StructuralTiltAnalyzer.StructuralTiltResult? = null,
    val riskPoints: Float = 0f,        // NEW
    val tiltPoints: Float = 0f,        // NEW
    val finalRiskPoints: Float = 0f    // NEW
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
    private var assessmentSummary: AssessmentSummary? = null

    // ✅ ADD REACTIVE STATE FOR BUILDING INFO
    private val _assessmentName = mutableStateOf("")
    private val _buildingType = mutableStateOf("")
    private val _constructionYear = mutableStateOf("")
    private val _renovationYear = mutableStateOf("")
    private val _floors = mutableStateOf("")
    private val _material = mutableStateOf("")
    private val _foundation = mutableStateOf("")
    private val _environment = mutableStateOf("")
    private val _previousIssues = mutableStateOf<List<String>>(emptyList())
    private val _occupancy = mutableStateOf("")
    private val _environmentalRisks = mutableStateOf<List<String>>(emptyList())
    private val _notes = mutableStateOf("")
    // ✅ ADD THESE THREE NEW STATE VARIABLES:
    private val _address = mutableStateOf("")
    private val _footprintArea = mutableStateOf("")
    private val _typeOfConstruction = mutableStateOf<List<String>>(emptyList())

    // ✅ ADD THIS LAUNCHER
    val editBuildingInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            if (data.getBooleanExtra("UPDATED", false)) {
                // ✅ UPDATE REACTIVE STATE (triggers UI recomposition automatically)
                _assessmentName.value = data.getStringExtra("assessmentName") ?: ""
                _buildingType.value = data.getStringExtra(IntentKeys.BUILDING_TYPE) ?: ""
                _constructionYear.value = data.getStringExtra(IntentKeys.CONSTRUCTION_YEAR) ?: ""
                _renovationYear.value = data.getStringExtra(IntentKeys.RENOVATION_YEAR) ?: ""
                _floors.value = data.getStringExtra(IntentKeys.FLOORS) ?: ""
                _material.value = data.getStringExtra(IntentKeys.MATERIAL) ?: ""
                _foundation.value = data.getStringExtra(IntentKeys.FOUNDATION) ?: ""
                _environment.value = data.getStringExtra(IntentKeys.ENVIRONMENT) ?: ""
                _previousIssues.value = data.getStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES) ?: emptyList()
                _occupancy.value = data.getStringExtra(IntentKeys.OCCUPANCY) ?: ""
                _environmentalRisks.value = data.getStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS) ?: emptyList()
                _notes.value = data.getStringExtra(IntentKeys.NOTES) ?: ""
                // ✅ ADD THESE THREE:
                _address.value = data.getStringExtra(IntentKeys.ADDRESS) ?: ""
                _footprintArea.value = data.getStringExtra(IntentKeys.FOOTPRINT_AREA) ?: ""
                _typeOfConstruction.value = data.getStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION) ?: emptyList()

                // ✅ ALSO UPDATE INTENT (for Firebase saving later)
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
                // Also update intent
                intent.putExtra(IntentKeys.ADDRESS, data.getStringExtra(IntentKeys.ADDRESS))
                intent.putExtra(IntentKeys.FOOTPRINT_AREA, data.getStringExtra(IntentKeys.FOOTPRINT_AREA))
                intent.putStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION, data.getStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION))

                Toast.makeText(this, "Building information updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        _assessmentName.value = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"
        val buildingAreas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, BuildingArea::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS)
        } ?: arrayListOf()

        originalBuildingAreas = buildingAreas

        _buildingType.value = intent.getStringExtra(IntentKeys.BUILDING_TYPE) ?: ""
        _constructionYear.value = intent.getStringExtra(IntentKeys.CONSTRUCTION_YEAR) ?: ""
        _renovationYear.value = intent.getStringExtra(IntentKeys.RENOVATION_YEAR) ?: ""
        _floors.value = intent.getStringExtra(IntentKeys.FLOORS) ?: ""
        _material.value = intent.getStringExtra(IntentKeys.MATERIAL) ?: ""
        _foundation.value = intent.getStringExtra(IntentKeys.FOUNDATION) ?: ""
        _environment.value = intent.getStringExtra(IntentKeys.ENVIRONMENT) ?: ""
        _previousIssues.value = intent.getStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES) ?: arrayListOf()
        _occupancy.value = intent.getStringExtra(IntentKeys.OCCUPANCY) ?: ""
        _environmentalRisks.value = intent.getStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS) ?: arrayListOf()
        _notes.value = intent.getStringExtra(IntentKeys.NOTES) ?: ""
        // ✅ ADD THESE THREE:
        _address.value = intent.getStringExtra(IntentKeys.ADDRESS) ?: ""
        _footprintArea.value = intent.getStringExtra(IntentKeys.FOOTPRINT_AREA) ?: ""
        _typeOfConstruction.value = intent.getStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION) ?: arrayListOf()

        // NEW: Check if this assessment has used re-analysis
        val hasUsedReanalysis = currentAssessmentId?.let {
            sharedPrefs.getBoolean("reanalyzed_$it", false)
        } ?: false

        setContent {
            MaterialTheme {
                // ✅ PASS STATE VALUES INSTEAD OF INTENT VALUES
                AssessmentResultsScreen(
                    buildingAreas = buildingAreas,
                    assessmentName = _assessmentName.value, // ✅ Use state
                    buildingType = _buildingType.value,     // ✅ Use state
                    constructionYear = _constructionYear.value,
                    renovationYear = _renovationYear.value,
                    floors = _floors.value,
                    material = _material.value,
                    foundation = _foundation.value,
                    environment = _environment.value,
                    previousIssues = _previousIssues.value,
                    occupancy = _occupancy.value,
                    environmentalRisks = _environmentalRisks.value,
                    notes = _notes.value,
                    // ✅ ADD THESE THREE:
                    address = _address.value,
                    footprintArea = _footprintArea.value,
                    typeOfConstruction = _typeOfConstruction.value,
                    assessmentId = currentAssessmentId,
                    onDownloadPdf = { downloadPdfReport() },  // Pass the function
                    initialHasReanalyzed = hasUsedReanalysis,
                    onSaveToFirebase = { summary, isReanalysis ->
                        saveAssessmentToFirebase(
                            _assessmentName.value, summary, _buildingType.value,
                            _constructionYear.value, _renovationYear.value, _floors.value,
                            _material.value, _foundation.value, _environment.value,
                            _previousIssues.value, _occupancy.value,
                            _environmentalRisks.value, _notes.value,
                            _address.value,           // ✅ ADD
                            _footprintArea.value,     // ✅ ADD
                            _typeOfConstruction.value, // ✅ ADD
                            isReanalysis
                        )
                    },
                    onMarkReanalyzed = {
                        currentAssessmentId?.let { id ->
                            sharedPrefs.edit().putBoolean("reanalyzed_$id", true).apply()
                        }
                    },
                    onEditBuildingInfo = {
                        val intent = Intent(this, EditBuildingInfoActivity::class.java).apply {
                            putExtra("assessmentName", _assessmentName.value)
                            putExtra(IntentKeys.BUILDING_TYPE, _buildingType.value)
                            putExtra(IntentKeys.CONSTRUCTION_YEAR, _constructionYear.value)
                            putExtra(IntentKeys.RENOVATION_YEAR, _renovationYear.value)
                            putExtra(IntentKeys.FLOORS, _floors.value)
                            putExtra(IntentKeys.MATERIAL, _material.value)
                            putExtra(IntentKeys.FOUNDATION, _foundation.value)
                            putExtra(IntentKeys.ENVIRONMENT, _environment.value)
                            putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, ArrayList(_previousIssues.value))
                            putExtra(IntentKeys.OCCUPANCY, _occupancy.value)
                            putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, ArrayList(_environmentalRisks.value))
                            putExtra(IntentKeys.NOTES, _notes.value)
                            // ✅ ADD THESE THREE:
                            putExtra(IntentKeys.ADDRESS, _address.value)
                            putExtra(IntentKeys.FOOTPRINT_AREA, _footprintArea.value)
                            putStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION, ArrayList(_typeOfConstruction.value))
                            putExtra("ASSESSMENT_ID", currentAssessmentId)
                        }
                        editBuildingInfoLauncher.launch(intent)
                    }
                )
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
        address: String,                      // ✅ ADD
        footprintArea: String,                // ✅ ADD
        typeOfConstruction: List<String>,
        isReanalysis: Boolean = false  // ✅ Use this parameter instead of intent
    ): Boolean {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) return false
        val userId = currentUser.uid

        return try {
            // ✅ FIXED: Use the isReanalysis parameter passed from the function
            val isActualReanalysis = isReanalysis && currentAssessmentId != null

            // Safety net: if we already have an ID and this is not a re-analysis,
            // do NOT create another assessment document.
            if (!isActualReanalysis && currentAssessmentId != null) {
                Log.d("FirebaseSave", "Skipping duplicate initial save for $currentAssessmentId")
                return true
            }

            val assessmentId = if (isActualReanalysis) {
                currentAssessmentId!!
            } else {
                UUID.randomUUID().toString().also { currentAssessmentId = it }
            }

            val areasData = mutableListOf<HashMap<String, Any>>()

            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                summary.areaAnalyses.forEach { areaAnalysis ->
                    val imagesData = mutableListOf<HashMap<String, Any>>()

                    areaAnalysis.imageAssessments.forEachIndexed { index, assessment ->
                        val firebaseImageUrl =
                            if (isActualReanalysis && assessment.firebaseImageUrl.isNotEmpty()) {
                                assessment.firebaseImageUrl
                            } else {
                                uploadImageToStorage(
                                    assessment.imageUri,
                                    userId,
                                    assessmentId,
                                    areaAnalysis.areaId,
                                    index
                                ) ?: throw Exception("Failed to upload image index $index")
                            }

                        // Group issues by type-level for this image
                        val issueGroups = assessment.detectedIssues.groupBy { "${it.damageType}-${it.damageLevel}" }

                        val recommendationsForImage =
                            if (assessment.detectedIssues.isEmpty()) {
                                listOf(
                                    hashMapOf(
                                        "title" to "Clean Surface",
                                        "description" to "No structural damage or surface deterioration detected.",
                                        "severity" to "GOOD",
                                        "actions" to listOf(
                                            "Continue regular maintenance schedule",
                                            "Monitor during routine inspections",
                                            "No immediate action required"
                                        ),
                                        // Extra metadata expected by AssessmentDetailsActivity
                                        "imageCount" to 1,
                                        "avgConfidence" to 0f
                                    )
                                )
                            } else {
                                issueGroups.map { (_, issues) ->
                                    val firstIssue = issues.first()
                                    val rec = getRecommendation(firstIssue.damageType, firstIssue.damageLevel)
                                    hashMapOf(
                                        "title" to rec.title,
                                        "description" to rec.description,
                                        "severity" to rec.severity,
                                        "actions" to rec.actions,
                                        // Number of images and average confidence for this issue group
                                        "imageCount" to issues.size,
                                        "avgConfidence" to issues.map { it.confidence }.average().toFloat()
                                    )
                                }
                            }

                        val imageData = hashMapOf<String, Any>(
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
                            "recommendations" to recommendationsForImage,
                            "riskPoints" to assessment.riskPoints,
                            "tiltPoints" to assessment.tiltPoints,
                            "finalRiskPoints" to assessment.finalRiskPoints,
                        )

                        // Optional plain surface confidence (if you have a "Plain" class)
                        imageData["plainConf"] = assessment.detectedIssues
                            .firstOrNull { it.damageType == "Plain" }?.confidence ?: 0f

                        // Pull image-level metadata (locationName, etc.) from original building areas
                        val originalArea = originalBuildingAreas?.find { it.id == areaAnalysis.areaId }
                        val photoMeta = originalArea?.photoMetadata?.getOrNull(index)

                        if (!photoMeta?.locationName.isNullOrBlank()) {
                            imageData["locationName"] = photoMeta!!.locationName
                        }

                        // Structural tilt metadata
                        assessment.structuralTilt?.let { tilt ->
                            imageData["structuralVerticalTilt"] = tilt.averageVerticalTilt
                            imageData["structuralHorizontalTilt"] = tilt.averageHorizontalTilt
                            imageData["structuralTiltSeverity"] = tilt.tiltSeverity.name
                            imageData["structuralTiltConfidence"] = tilt.confidence
                            imageData["structuralLinesDetected"] = tilt.detectedLines
                            imageData["structuralTiltWarning"] = tilt.warning ?: ""

                            // ✅ NEW: Save friendly recommendations!
                            val tiltRecs = when (tilt.tiltSeverity) {
                                StructuralTiltAnalyzer.TiltSeverity.SEVERE ->
                                    listOf("Serious slant (needs expert)", "Building is leaning BADLY. GET EVERYONE OUT + call engineer TODAY")
                                StructuralTiltAnalyzer.TiltSeverity.MODERATE ->
                                    listOf("Noticeable tilt", "Walls leaning enough to worry. Call building checker this week")
                                StructuralTiltAnalyzer.TiltSeverity.MINOR ->
                                    listOf("Slight tilt", "Slight lean - just keep an eye on it next time you check")
                                else -> listOf("GOOD - Looks straight")
                            }
                            imageData["tiltRecommendations"] = tiltRecs  // ✅ SAVED!
                        }

                        imagesData.add(imageData)
                    }

                    // Area-level metadata
                    val originalArea = originalBuildingAreas?.find { it.id == areaAnalysis.areaId }

                    val areaMap = hashMapOf<String, Any>(
                        "areaId" to areaAnalysis.areaId,
                        "areaName" to areaAnalysis.areaName,
                        "areaType" to areaAnalysis.areaType.name,
                        "areaRisk" to areaAnalysis.areaRisk,
                        "structuralAnalysisEnabled" to areaAnalysis.structuralAnalysisEnabled,
                        "avgRiskPoints" to areaAnalysis.imageAssessments.map { it.finalRiskPoints }.average(),
                        "images" to imagesData
                    )

                    // Add extra area metadata if your BuildingArea has these fields
                    originalArea?.let { oa ->
                        if (!oa.description.isNullOrBlank()) {
                            areaMap["areaDescription"] = oa.description
                        }
                    }

                    areasData.add(areaMap)
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
                "notes" to notes,
                // ✅ ADD THESE THREE:
                "address" to address,
                "footprintArea" to footprintArea,
                "typeOfConstruction" to typeOfConstruction
            )

            // Save to Firestore
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (isActualReanalysis) {
                    // ✅ UPDATE existing document
                    val docRef = firestore.collection("users")
                        .document(userId)
                        .collection("assessments")
                        .document(assessmentId)

                    val currentDoc = docRef.get().await()
                    val currentReanalysisCount = currentDoc.getLong("reanalysisCount")?.toInt() ?: 0

                    // Add incremented count to assessment data
                    assessmentData["reanalysisCount"] = currentReanalysisCount + 1
                    assessmentData["lastReanalysisDate"] = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())

                    // ✅ Use update instead of set
                    docRef.update(assessmentData as Map<String, Any>).await()

                    Log.d("FirebaseUpload", "✅ Re-analysis updated successfully for ID: $assessmentId")
                } else {
                    // ✅ CREATE new document
                    assessmentData["reanalysisCount"] = 0

                    firestore.collection("users")
                        .document(userId)
                        .collection("assessments")
                        .document(assessmentId)
                        .set(assessmentData)
                        .await()

                    Log.d("FirebaseUpload", "✅ New assessment created with ID: $assessmentId")
                }
            }

            true
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Upload failed", e)
            false
        }
    }

    // ✅ COMPLETE FIXED FUNCTION - PASTE THIS ENTIRE BLOCK
    // ✅ COMPLETE FIXED FUNCTION - COPY PASTE THIS ENTIRE BLOCK
    suspend fun generatePdfWithAllData(): PdfAssessmentData? {
        // ✅ FIXED: Fallback if no summary available
        val summary = assessmentSummary ?: run {
            Log.w("PDF", "No assessment summary - using fallback data")
            return PdfAssessmentData(
                assessmentName = _assessmentName.value,
                date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                overallRisk = "INSPECTED",
                totalIssues = 0,
                crackHighCount = 0,
                crackModerateCount = 0,
                crackLowCount = 0,
                paintCount = 0,
                algaeCount = 0,
                buildingType = _buildingType.value,
                constructionYear = _constructionYear.value,
                renovationYear = _renovationYear.value,
                floors = _floors.value,
                material = _material.value,
                foundation = _foundation.value,
                environment = _environment.value,
                previousIssues = _previousIssues.value.joinToString(", "),
                occupancy = _occupancy.value,
                environmentalRisks = _environmentalRisks.value.joinToString(", "),
                notes = _notes.value,
                address = _address.value,
                footprintArea = _footprintArea.value,
                typeOfConstruction = _typeOfConstruction.value.joinToString(", "),
                areasData = emptyList(),
                imageDetails = emptyList()
            )
        }

        // Count damage types
        var crackHighCount = 0
        var crackModerateCount = 0
        var crackLowCount = 0
        var paintCount = 0
        var algaeCount = 0

        summary.areaAnalyses.forEach { areaAnalysis ->
            areaAnalysis.imageAssessments.forEach { imageAssessment ->
                imageAssessment.detectedIssues.forEach { detectedIssue ->
                    val damageType = detectedIssue.damageType.lowercase()
                    val damageLevel = detectedIssue.damageLevel

                    when {
                        damageType.contains("spalling") -> crackHighCount++
                        damageType.contains("crack") && damageLevel == "High" -> crackHighCount++
                        damageType.contains("crack") && damageLevel == "Moderate" -> crackModerateCount++
                        damageType.contains("crack") && damageLevel == "Low" -> crackLowCount++
                        damageType.contains("paint") -> paintCount++
                        damageType.contains("algae") -> algaeCount++
                    }
                }
            }
        }

        // Create AreaSummary list
        val areasData = summary.areaAnalyses.map { areaAnalysis ->
            val maxTilt = areaAnalysis.imageAssessments
                .mapNotNull { it.structuralTilt?.averageVerticalTilt }
                .maxOrNull()

            val maxTiltSeverity = areaAnalysis.imageAssessments
                .mapNotNull { it.structuralTilt?.tiltSeverity?.name }
                .maxByOrNull { it ?: "" }

            val allIssues = areaAnalysis.imageAssessments.flatMap { it.detectedIssues }
                .map { "${it.damageType} (${it.damageLevel})" }
                .distinct()

            AreaSummary(
                areaName = areaAnalysis.areaName,
                areaRisk = areaAnalysis.areaRisk,
                avgRiskPoints = areaAnalysis.imageAssessments.map { it.finalRiskPoints }.average().toFloat(),
                imageCount = areaAnalysis.imageAssessments.size,
                structuralAnalysisEnabled = areaAnalysis.structuralAnalysisEnabled,
                detectedIssues = allIssues,
                maxTiltAngle = maxTilt?.toDouble(),
                maxTiltSeverity = maxTiltSeverity
            )
        }

        // Create ImageDetail list
        val imageDetails = summary.areaAnalyses.flatMap { areaAnalysis ->
            areaAnalysis.imageAssessments.mapIndexed { index, imageAssessment ->
                ImageDetail(
                    imageUrl = imageAssessment.imageUri.toString(),
                    locationName = "Image_${index + 1}",
                    areaName = areaAnalysis.areaName
                )
            }
        }

        // ✅ RETURN COMPLETE PDF DATA
        return PdfAssessmentData(
            assessmentName = _assessmentName.value,
            date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
            overallRisk = summary.overallRisk,
            totalIssues = summary.totalIssues,
            crackHighCount = crackHighCount,
            crackModerateCount = crackModerateCount,
            crackLowCount = crackLowCount,
            paintCount = paintCount,
            algaeCount = algaeCount,
            buildingType = _buildingType.value,
            constructionYear = _constructionYear.value,
            renovationYear = _renovationYear.value,
            floors = _floors.value,
            material = _material.value,
            foundation = _foundation.value,
            environment = _environment.value,
            previousIssues = _previousIssues.value.joinToString(", "),
            occupancy = _occupancy.value,
            environmentalRisks = _environmentalRisks.value.joinToString(", "),
            notes = _notes.value,
            address = _address.value,
            footprintArea = _footprintArea.value,
            typeOfConstruction = _typeOfConstruction.value.joinToString(", "),
            areasData = areasData,
            imageDetails = imageDetails
        )
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

    // Add this function to AssessmentResultsActivity class
// ADD THIS ENTIRE FUNCTION TO AssessmentResultsActivity class (around line 500-600)
    suspend fun loadAssessmentDataFromFirebase(assessmentId: String): PdfAssessmentData? {
        return try {
            val userId = firebaseAuth.currentUser?.uid ?: return null
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("assessments")
                .document(assessmentId)

            val document = docRef.get().await()

            if (!document.exists()) {
                Log.w("LoadAssessment", "Document not found: $assessmentId")
                return null
            }

            // Extract areas data
            val areasRaw = document.get("areas") as? List<HashMap<String, Any>> ?: emptyList()

            // Build AreaSummary list
            val areasData = areasRaw.map { areaMap ->
                val imagesList = areaMap["images"] as? List<*> ?: emptyList<Any>()
                val detectedIssuesList = mutableListOf<String>()

                imagesList.forEach { imgItem ->
                    if (imgItem is Map<*, *>) {
                        val issues = imgItem["detectedIssues"] as? List<*> ?: emptyList<Any>()
                        issues.forEach { issueItem ->
                            if (issueItem is Map<*, *>) {
                                val type = issueItem["type"] as? String ?: "Unknown"
                                val level = issueItem["level"] as? String ?: ""
                                detectedIssuesList.add("$type ($level)".trim())
                            }
                        }
                    }
                }

                AreaSummary(
                    areaName = areaMap["areaName"] as? String ?: "Unknown Area",
                    areaRisk = areaMap["areaRisk"] as? String ?: "Low Risk",
                    avgRiskPoints = (areaMap["avgRiskPoints"] as? Number)?.toFloat() ?: 0f,
                    imageCount = imagesList.size,
                    structuralAnalysisEnabled = areaMap["structuralAnalysisEnabled"] as? Boolean ?: false,
                    detectedIssues = detectedIssuesList.distinct(),
                    maxTiltAngle = null,
                    maxTiltSeverity = null
                )
            }

            // Build ImageDetail list (THIS IS WHAT MAKES THE 2MB PDF)
            val imageDetails = mutableListOf<ImageDetail>()
            areasRaw.forEachIndexed { areaIndex, areaMap ->
                val imagesList = areaMap["images"] as? List<*> ?: emptyList<Any>()
                imagesList.forEachIndexed { imgIndex, imgItem ->
                    if (imgItem is Map<*, *>) {
                        val imageUrl = imgItem["imageUri"] as? String ?: ""
                        val locationName = imgItem["locationName"] as? String ?: ""
                        val areaName = areaMap["areaName"] as? String ?: "Area ${areaIndex + 1}"

                        if (imageUrl.isNotEmpty()) {
                            imageDetails.add(
                                ImageDetail(
                                    imageUrl = imageUrl,
                                    areaName = areaName,
                                    locationName = locationName,
                                    imageNumber = imgIndex + 1,
                                    totalImages = imagesList.size
                                )
                            )
                        }
                    }
                }
            }

            // Count damage types from Firestore
            var crackHighCount = 0
            var crackModerateCount = 0
            var crackLowCount = 0
            var paintCount = 0
            var algaeCount = 0

            areasRaw.forEach { areaMap ->
                val imagesList = areaMap["images"] as? List<*> ?: emptyList<Any>()
                imagesList.forEach { imgItem ->
                    if (imgItem is Map<*, *>) {
                        val issues = imgItem["detectedIssues"] as? List<*> ?: emptyList<Any>()
                        issues.forEach { issueItem ->
                            if (issueItem is Map<*, *>) {
                                val type = (issueItem["type"] as? String ?: "").lowercase()
                                val level = issueItem["level"] as? String ?: ""

                                when {
                                    type.contains("spalling") -> crackHighCount++
                                    type.contains("crack") && level == "High" -> crackHighCount++
                                    type.contains("crack") && level == "Moderate" -> crackModerateCount++
                                    type.contains("crack") && level == "Low" -> crackLowCount++
                                    type.contains("paint") -> paintCount++
                                    type.contains("algae") -> algaeCount++
                                }
                            }
                        }
                    }
                }
            }

            // Build complete PDF data
            PdfAssessmentData(
                assessmentName = document.getString("assessmentName") ?: "Assessment",
                date = document.getString("date") ?: "",
                overallRisk = document.getString("overallRisk") ?: "INSPECTED",
                totalIssues = document.getLong("totalIssues")?.toInt() ?: 0,
                crackHighCount = crackHighCount,
                crackModerateCount = crackModerateCount,
                crackLowCount = crackLowCount,
                paintCount = paintCount,
                algaeCount = algaeCount,
                buildingType = document.getString("buildingType") ?: "",
                constructionYear = document.getString("constructionYear") ?: "",
                renovationYear = document.getString("renovationYear") ?: "",
                floors = document.getString("floors") ?: "",
                material = document.getString("material") ?: "",
                foundation = document.getString("foundation") ?: "",
                environment = document.getString("environment") ?: "",
                previousIssues = (document.get("previousIssues") as? List<*>)?.joinToString(", ") ?: "",
                occupancy = document.getString("occupancy") ?: "",
                environmentalRisks = (document.get("environmentalRisks") as? List<*>)?.joinToString(", ") ?: "",
                notes = document.getString("notes") ?: "",
                address = document.getString("address") ?: "",
                footprintArea = document.getString("footprintArea") ?: "",
                typeOfConstruction = document.getString("typeOfConstruction") ?: "",
                areasData = areasData,
                imageDetails = imageDetails
            )

        } catch (e: Exception) {
            Log.e("LoadAssessment", "Error loading from Firestore", e)
            null
        }
    }

    // ADD THIS FUNCTION (right after the loadAssessmentDataFromFirebase function)
    fun downloadPdfReport() {
        val assessmentId = currentAssessmentId
        if (assessmentId.isNullOrEmpty()) {
            Toast.makeText(this, "No assessment ID found", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading dialog
        val loadingDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Generating PDF...")
            .setMessage("Loading assessment data and images...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()).launch {
            try {
                // Load real data from Firestore
                val pdfData = loadAssessmentDataFromFirebase(assessmentId)

                loadingDialog.dismiss()

                if (pdfData != null) {
                    Log.d("PDFDownload", "✅ Loaded: ${pdfData.imageDetails.size} images, ${pdfData.areasData.size} areas")

                    // Generate PDF
                    val pdfPath = PdfReportGenerator.generatePdfReport(this@AssessmentResultsActivity, pdfData)

                    if (pdfPath != null) {
                        val fileSizeKB = java.io.File(pdfPath).length() / 1024
                        Toast.makeText(
                            this@AssessmentResultsActivity,
                            "PDF saved to downloads! (${fileSizeKB}KB with ${pdfData.imageDetails.size} images)",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("PDFDownload", "SUCCESS: $pdfPath (${fileSizeKB}KB)")
                    } else {
                        Toast.makeText(this@AssessmentResultsActivity, "❌ Failed to generate PDF", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@AssessmentResultsActivity, "❌ No assessment data found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("PDFDownload", "Error", e)
                Toast.makeText(this@AssessmentResultsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

    // ✅ ADD THESE THREE:
    address: String = "",
    footprintArea: String = "",
    typeOfConstruction: List<String> = emptyList(),

    assessmentId: String? = null, // NEW: Receive assessment ID
    onDownloadPdf: () -> Unit = {},  // Download callback
    initialHasReanalyzed: Boolean = false, // NEW: Receive re-analysis state
    onSaveToFirebase: (AssessmentSummary, Boolean) -> Boolean = { _, _ -> false },
    onReanalyze: () -> Unit = {},
    onMarkReanalyzed: () -> Unit = {}, // NEW: Callback to mark re-analysis used
    onEditBuildingInfo: () -> Unit = {} // ✅ ADD THIS PARAMETER
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? AssessmentResultsActivity
    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isReanalyzing by remember { mutableStateOf(false) }
    var assessmentSummary by remember { mutableStateOf<AssessmentSummary?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var isSavedToFirebase by remember { mutableStateOf(false) }
    var showReanalyzeDialog by remember { mutableStateOf(false) }
    var hasReanalyzed by remember { mutableStateOf(initialHasReanalyzed) } // FIXED: Use initial value
    var loadingMessage by remember { mutableStateOf("Analyzing images, please wait...") }
    var allImagesCount by remember { mutableStateOf(0) }

    val SHOW_THRESHOLD = 0.01f

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

    // ✅ STEP 2: ADD THESE TWO FUNCTIONS HERE
    fun getDamagePoints(detectedIssues: List<DetectedIssue>): Float {
        var points = 0f
        detectedIssues.forEach { issue ->
            when ("${issue.damageType}-${issue.damageLevel}") {
                "Spalling-High", "Major Crack-High" -> points += 3f
                "Algae-Moderate" -> points += 2f
                "Minor Crack-Low", "Paint Damage-Low" -> points += 1f
            }
        }
        return points
    }

    fun getTiltPoints(tilt: StructuralTiltAnalyzer.StructuralTiltResult?): Float {
        return tilt?.let {
            StructuralTiltAnalyzer().getRiskPoints(it)  // ✅ Uses new analyzer method
        } ?: 0f
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
            val detectedIssues = detectedIssuesMutable.toList()  // ✅ 1. CREATE detectedIssues
            val damagePoints = getDamagePoints(detectedIssues)
            val tiltPoints = getTiltPoints(structuralTilt)
            val finalPoints = maxOf(damagePoints, tiltPoints)
            val imageRisk = when {
                detectedIssuesMutable.any { it.damageType == "Spalling" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Major Crack" && it.damageLevel == "High" } -> "High"
                detectedIssuesMutable.any { it.damageType == "Algae" } -> "Moderate"
                detectedIssuesMutable.any { it.damageType == "Minor Crack" || it.damageType == "Paint Damage" } -> "Low"
                detectedIssuesMutable.isNotEmpty() -> "Low"
                plainConf >= SHOW_THRESHOLD -> "None"
                else -> "Low"
            }

            val imageRiskString = when {
                finalPoints >= 2f -> "SEVERE"
                finalPoints >= 1f -> "MODERATE"
                else -> "MINOR"
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
                imageRisk = imageRiskString,  // ✅ NEW - uses scoring system
                structuralTilt = structuralTilt,
                riskPoints = damagePoints,
                tiltPoints = tiltPoints,
                finalRiskPoints = finalPoints
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

                val imageCount = imageAssessments.size
                val avgPoints = if (imageCount >= 3) {
                    imageAssessments.map { it.finalRiskPoints }.average().toFloat()
                } else 0f

                val areaRisk = when {
                    imageCount < 3 -> "INSUFFICIENT"
                    avgPoints >= 3.0f -> "SEVERE"
                    avgPoints >= 2.0f -> "MODERATE"
                    else -> "MINOR"
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
            val severeCount = areaAnalysesList.count { it.areaRisk == "SEVERE" }
            val moderateCount = areaAnalysesList.count { it.areaRisk == "MODERATE" }
            val overallRisk = when {
                areaAnalysesList.any { it.areaRisk == "SEVERE" } -> "UNSAFE"
                moderateCount >= 2 -> "RESTRICTED"
                else -> "INSPECTED"
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
                        allImagesCount = summary.areaAnalyses.sumOf { it.imageAssessments.size }
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
        // 1) If we already have a summary in memory, never re-analyze.
        if (assessmentSummary != null) {
            isSavedToFirebase = true
            return@LaunchedEffect
        }

        // 2) If this assessment already exists in Firestore, do not auto-analyze.
        if (assessmentId != null) {
            // Later you can load summary from Firestore here.
            isSavedToFirebase = true
            return@LaunchedEffect
        }

        // 3) New assessment: run analysis + initial save ONCE.
        if (buildingAreas.isNotEmpty() && !isSavedToFirebase) {
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

                    val imageCount = imageAssessments.size
                    val avgPoints = if (imageCount >= 3) {
                        imageAssessments.map { it.finalRiskPoints }.average().toFloat()
                    } else 0f

                    val areaRisk = when {
                        imageCount < 3 -> "INSUFFICIENT"
                        avgPoints >= 2.0f -> "SEVERE"
                        avgPoints >= 1.0f -> "MODERATE"
                        else -> "MINOR"
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

                val severeCount = areaAnalysesList.count { it.areaRisk == "SEVERE" }
                val moderateCount = areaAnalysesList.count { it.areaRisk == "MODERATE" }

                val overallRisk = when {
                    areaAnalysesList.any { it.areaRisk == "SEVERE" } -> "UNSAFE"
                    moderateCount >= 2 -> "RESTRICTED"
                    else -> "INSPECTED"
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
                            assessmentSummary = summary              // mark as computed
                            isSavedToFirebase = true
                            allImagesCount = summary.areaAnalyses.sumOf { it.imageAssessments.size }
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
                    text =  assessmentName,
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

                // ⭐ ADD THIS STATE VARIABLE at top with other states (line ~150)

// ⭐ OVERALL RISK CARD - Fixed smart cast + state
                if (assessmentSummary != null) {
                    val summary = assessmentSummary!!  // ✅ Local copy = smart cast works!
                    val riskColor = when (summary.overallRisk) {
                        "UNSAFE" -> Color(0xFFD32F2F)
                        "RESTRICTED" -> Color(0xFFF57C00)
                        "INSPECTED" -> Color(0xFF388E3C)
                        else -> Color(0xFF388E3C)
                    }
                    val riskBgColor = when (summary.overallRisk) {
                        "UNSAFE" -> Color(0xFFFFEBEE)
                        "RESTRICTED" -> Color(0xFFFFF3E0)
                        "INSPECTED" -> Color(0xFFE8F5E9)
                        else -> Color(0xFFE8F5E9)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = riskBgColor),
                        border = BorderStroke(2.dp, riskColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = riskColor,
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
                                text = buildString {
                                    append("Analysis of $allImagesCount ${if (allImagesCount == 1) "photo" else "photos"} completed.")
                                    if (summary.totalIssues > 0) {
                                        append(" ${summary.totalIssues} ${if (summary.totalIssues == 1) "area" else "areas"} of concern detected.")
                                    } else {
                                        append(" No significant issues detected.")
                                    }
                                },
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }



                // Building Information Card
                BuildingInformationCard(
                    buildingType = buildingType,
                    constructionYear = constructionYear,
                    renovationYear = renovationYear,
                    floors = floors,
                    material = material,
                    foundation = foundation,
                    environment = environment,
                    occupancy = occupancy,
                    previousIssues = previousIssues,
                    environmentalRisks = environmentalRisks,
                    notes = notes,

                    // ✅ ADD THESE THREE:
                    address = address,
                    footprintArea = footprintArea,
                    typeOfConstruction = typeOfConstruction,

                    assessmentName =  assessmentName,
                    onEditClick = { onEditBuildingInfo() },
                    initiallyExpanded = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Detection Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFFFF3E0)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFF57C00),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Detection Summary",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                            }

                            if (summary.detectionSummary.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = "${summary.totalIssues} ${if (summary.totalIssues == 1) "Issue" else "Issues"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (summary.detectionSummary.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No visible damage detected",
                                    fontSize = 14.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            summary.detectionSummary.forEach { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF9FAFB)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = when {
                                                    item.damageType.contains("Spalling") ||
                                                            item.damageType.contains("Major Crack") -> Color(0xFFFFEBEE)
                                                    item.damageType.contains("Algae") -> Color(0xFFFFF3E0)
                                                    else -> Color(0xFFF0FDF4)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${item.count}",
                                                        color = when {
                                                            item.damageType.contains("Spalling") ||
                                                                    item.damageType.contains("Major Crack") -> Color(0xFFDC2626)
                                                            item.damageType.contains("Algae") -> Color(0xFFF59E0B)
                                                            else -> Color(0xFF16A34A)
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = item.damageType,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = "Avg Confidence: ${(item.avgConfidence * 100).toInt()}%",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF6B7280)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Structural Tilt Summary
                if (summary.areaAnalyses.any { it.structuralAnalysisEnabled }) {
                    StructuralTiltSummaryCard(summary = summary)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ✅ SIMPLE HEADER - NO BACKGROUND!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT: Icon + Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Area-by-Area Analysis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }

                    // RIGHT: Area count
                    Text(
                        text = "${summary.areaAnalyses.size} areas",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Area Analysis Cards
                summary.areaAnalyses.forEach { areaAnalysis ->
                    var areaExpanded by remember { mutableStateOf(true) }

                    val areaRiskColor = when {
                        areaAnalysis.areaRisk == "SEVERE" -> Color(0xFFD32F2F)
                        areaAnalysis.areaRisk == "MODERATE" -> Color(0xFFF57C00)
                        areaAnalysis.areaRisk == "MINOR" -> Color(0xFF388E3C)
                        areaAnalysis.areaRisk == "INSUFFICIENT" -> Color(0xFF6B7280)
                        else -> Color(0xFF388E3C)
                    }

                    val areaHeaderBackgroundColor = when {
                        areaAnalysis.areaRisk == "SEVERE" -> Color(0xFFFFEBEE)
                        areaAnalysis.areaRisk == "MODERATE" -> Color(0xFFFFF3E0)
                        areaAnalysis.areaRisk == "MINOR" -> Color(0xFFE8F5E9)
                        areaAnalysis.areaRisk == "INSUFFICIENT" -> Color(0xFFF1F5F9)
                        else -> Color(0xFFE8F5E9)
                    }

                    // Determine area icon
                    val areaIcon = when {
                        areaAnalysis.areaType.name.contains("FOUNDATION") -> Icons.Default.Foundation
                        areaAnalysis.areaType.name.contains("WALL") -> Icons.Default.CropSquare
                        areaAnalysis.areaType.name.contains("COLUMN") -> Icons.Default.ViewColumn
                        areaAnalysis.areaType.name.contains("ROOF") -> Icons.Default.Roofing
                        else -> Icons.Default.Home
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, areaRiskColor.copy(alpha = 0.3f))
                    ) {
                        Column {
                            // ===== IMPROVED HEADER SECTION =====
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(areaHeaderBackgroundColor)
                                    .clickable { areaExpanded = !areaExpanded }
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon on top-left (smaller, cleaner)
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = Color.White,
                                    tonalElevation = 2.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = areaIcon,
                                            contentDescription = null,
                                            tint = areaRiskColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Text content takes remaining space
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Area Name (bold, prominent)
                                    Text(
                                        text = areaAnalysis.areaName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black,
                                        lineHeight = 22.sp,
                                        maxLines = 1
                                    )

                                    // Description (if exists) - wraps fully
                                    val area = buildingAreas.find { it.id == areaAnalysis.areaId }
                                    if (!area?.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = area!!.description,
                                            fontSize = 14.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 19.sp,
                                            maxLines = 2
                                        )
                                    }

                                    // Photo count (subtle)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = null,
                                            tint = Color(0xFF9CA3AF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (areaAnalysis.imageAssessments.size == 1)
                                                "1 photo analyzed"
                                            else
                                                "${areaAnalysis.imageAssessments.size} photos analyzed",
                                            fontSize = 13.sp,
                                            color = Color(0xFF9CA3AF),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Right side: Risk badge + expand icon (compact)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = areaRiskColor.copy(alpha = 0.12f),
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            text = areaAnalysis.areaRisk,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = areaRiskColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (areaExpanded)
                                            Icons.Default.ExpandLess
                                        else
                                            Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
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

                                    // Image Analysis Section
                                    Text(
                                        text = "Image Analysis",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF333333)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    areaAnalysis.imageAssessments.forEachIndexed { index, imageAssessment ->
                                        // ✅ NEW: Get location name from PhotoMetadata
                                        val area = buildingAreas.find { it.id == areaAnalysis.areaId }
                                        val photoMeta = area?.photoMetadata?.getOrNull(index)
                                        val displayName = if (photoMeta?.locationName?.isNotEmpty() == true) {
                                            photoMeta.locationName
                                        } else {
                                            "Image ${index + 1}"
                                        }

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
                                                    painter = rememberAsyncImagePainter(imageAssessment.imageUri),
                                                    contentDescription = displayName,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.Gray)
                                                        .clickable { showFullImage = true },
                                                    contentScale = ContentScale.Crop
                                                )

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    // ✅ UPDATED: Display location name with icon
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (photoMeta?.locationName?.isNotEmpty() == true) {
                                                                    Icons.Default.LocationOn
                                                                } else {
                                                                    Icons.Default.Image
                                                                },
                                                                contentDescription = null,
                                                                tint = Color(0xFF2196F3),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = displayName,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color.Black
                                                            )
                                                        }
                                                    }

                                                    // Structural Tilt Info
                                                    imageAssessment.structuralTilt?.let { structural ->
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
                                                                    "${structural.tiltSeverity.name} (${"%.1f".format(structural.averageVerticalTilt)}°)" +
                                                                            if (structural.cameraTiltCompensation != null) " 📱+camera corr." else "",
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

                                                    // Detected Issues
                                                    if (imageAssessment.detectedIssues.isEmpty()) {
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
                                                            text = "AI Confidence: ${(imageAssessment.confidence * 100).toInt()}%",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    } else {
                                                        imageAssessment.detectedIssues.forEach { issue ->
                                                            Text(
                                                                text = issue.damageType,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color.Black
                                                            )
                                                            Text(
                                                                text = "AI Confidence: ${(issue.confidence * 100).toInt()}%",
                                                                fontSize = 12.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Full Image Dialog
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
                                                                    modifier = Modifier.background(
                                                                        Color.White.copy(alpha = 0.2f),
                                                                        CircleShape
                                                                    )
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
                                                                painter = rememberAsyncImagePainter(imageAssessment.imageUri),
                                                                contentDescription = "Full $displayName",
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 16.dp),
                                                                contentScale = ContentScale.Fit
                                                            )

                                                            Spacer(modifier = Modifier.height(16.dp))

                                                            Text(
                                                                text = "$displayName - Tap anywhere to close",
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
                            fontSize = 10.sp,
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

    val severeCount = allAssessments.count {
        it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.SEVERE
    }
    val moderateCount = allAssessments.count {
        it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.MODERATE
    }
    val minorCount = allAssessments.count {
        it.structuralTilt?.tiltSeverity == StructuralTiltAnalyzer.TiltSeverity.MINOR
    }
    val totalImages = allAssessments.size
    val hasStructuralIssues = (severeCount + moderateCount + minorCount) > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Architecture,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Structural Tilt Check",  // ✅ USER-FRIENDLY TITLE
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ✅ SIMPLIFIED WARNING MESSAGE
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFEF3C7),
                modifier = Modifier.fillMaxWidth()
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
                        "This is a rough estimate from your phone camera. For accurate measurements, hire a professional with proper surveying tools.",  // ✅ SIMPLE & DIRECT
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ✅ RESULTS SUMMARY
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
                        "Walls and floors look level",  // ✅ EVERYDAY LANGUAGE
                        fontSize = 14.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    "Found tilt in ${severeCount + moderateCount + minorCount} of $totalImages checked photos",  // ✅ CLEAR COUNT
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(12.dp))

                // ✅ SIMPLIFIED SEVERITY ROWS
                if (severeCount > 0) {
                    TiltQualityRow(
                        label = "Serious slant (needs expert)",  // ✅ USER-FRIENDLY
                        description = "Structure is leaning BADLY. GET EVERYONE OUT + call engineer TODAY",
                        count = severeCount,
                        percent = (severeCount * 100f / totalImages).toInt(),
                        color = Color(0xFFEF4444)
                    )
                }

                if (moderateCount > 0) {
                    TiltQualityRow(
                        label = "Noticeable tilt",  // ✅ USER-FRIENDLY
                        description = "Structure leaning enough to worry. Call building checker this week",
                        count = moderateCount,
                        percent = (moderateCount * 100f / totalImages).toInt(),
                        color = Color(0xFFF59E0B)
                    )
                }

                if (minorCount > 0) {
                    TiltQualityRow(
                        label = "Slight tilt",  // ✅ USER-FRIENDLY
                        description = "Slight lean - just keep an eye on it next time you check",
                        count = minorCount,
                        percent = (minorCount * 100f / totalImages).toInt(),
                        color = Color(0xFF3B82F6)
                    )
                }
            }
        }
    }
}

// ✅ NEW SIMPLIFIED ROW COMPONENT
@Composable
fun TiltQualityRow(
    label: String,
    description: String,
    count: Int,
    percent: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = count.toString(),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF374151)
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 14.sp
                )
            }
        }

        Text(
            text = "$percent%",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
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

                // Inside ImageAnalysisCard, replace the structural tilt section:
                assessment.structuralTilt?.let { structural ->
                    if (structural.tiltSeverity != StructuralTiltAnalyzer.TiltSeverity.NONE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Architecture,
                                contentDescription = "Tilt detected",
                                tint = when (structural.tiltSeverity) {
                                    StructuralTiltAnalyzer.TiltSeverity.SEVERE -> Color(0xFFEF4444)
                                    StructuralTiltAnalyzer.TiltSeverity.MODERATE -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            // ✅ SIMPLIFIED TEXT
                            Text(
                                when (structural.tiltSeverity) {
                                    StructuralTiltAnalyzer.TiltSeverity.SEVERE ->
                                        "Serious tilt: ${structural.averageVerticalTilt.toInt()}°"
                                    StructuralTiltAnalyzer.TiltSeverity.MODERATE ->
                                        "Noticeable tilt: ${structural.averageVerticalTilt.toInt()}°"
                                    StructuralTiltAnalyzer.TiltSeverity.MINOR ->
                                        "Slight tilt: ${structural.averageVerticalTilt.toInt()}°"
                                    else -> "Level"
                                },
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
                        text = "AI Confidence: ${(assessment.confidence * 100).toInt()}%",
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
    // ✅ ADD THESE THREE PARAMETERS:
    address: String,
    footprintArea: String,
    typeOfConstruction: List<String>,
    assessmentName: String,
    onEditClick: () -> Unit,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
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
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Building Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    val rotation by animateFloatAsState(
                        targetValue = if (isExpanded) 0f else -90f,
                        label = "toggle"
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotation),
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ Check if any field has data
                    val hasData = listOf(
                        buildingType, constructionYear, renovationYear,
                        floors, material, foundation, environment, occupancy, notes, address, footprintArea
                    ).any { it.isNotBlank() } || previousIssues.isNotEmpty() || environmentalRisks.isNotEmpty() || typeOfConstruction.isNotEmpty()

                    if (!hasData) {
                        // ✅ UPDATED: Removed icon, adjusted description
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No building information added",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // ✅ ADD THESE DISPLAY ROWS (put them before buildingType):
                        if (address.isNotBlank()) BuildingInfoRow("Address", address)
                        if (footprintArea.isNotBlank()) BuildingInfoRow("Footprint Area", "$footprintArea sq ft")

                        if (buildingType.isNotBlank()) BuildingInfoRow("Building Type", buildingType)
                        if (constructionYear.isNotBlank()) BuildingInfoRow("Construction Year", constructionYear)
                        if (renovationYear.isNotBlank()) BuildingInfoRow("Renovation Year", renovationYear)
                        if (floors.isNotBlank()) BuildingInfoRow("Floors", floors)
                        if (material.isNotBlank()) BuildingInfoRow("Material", material)
                        if (foundation.isNotBlank()) BuildingInfoRow("Foundation", foundation)
                        if (environment.isNotBlank()) BuildingInfoRow("Environment", environment)
                        if (occupancy.isNotBlank()) BuildingInfoRow("Occupancy", occupancy)

                        if (previousIssues.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Previous Issues:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            previousIssues.forEach { issue ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text("• ", fontSize = 13.sp, color = Color(0xFF6B7280))
                                    Text(
                                        text = issue,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }

                        if (environmentalRisks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Environmental Risks:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            environmentalRisks.forEach { risk ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text("• ", fontSize = 13.sp, color = Color(0xFF6B7280))
                                    Text(
                                        text = risk,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }

                        // ✅ ADD THIS AFTER FLOORS (before material):
                        if (typeOfConstruction.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Type of Construction:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            typeOfConstruction.forEach { type ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text("• ", fontSize = 13.sp, color = Color(0xFF6B7280))
                                    Text(
                                        text = type,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }

                        if (notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Notes:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = notes,
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}