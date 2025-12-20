package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.structurescan.Utils.PdfReportGenerator
import com.example.structurescan.Utils.PdfAssessmentData

data class DetailDetectedIssue(
    val damageType: String = "Unknown",
    val damageLevel: String = "Low",
    val confidence: Float = 0f
)

data class DetailImageAssessment(
    val imageUrl: String = "",
    val detectedIssues: List<DetailDetectedIssue> = emptyList(),
    val imageRisk: String = "Low",
    val damageType: String = "Unknown",
    val damageLevel: String = "Low",
    val confidence: Float = 0f,
    val plainConf: Float = 0f,
    val recommendations: List<DetailRecommendation> = emptyList(),
    val structuralVerticalTilt: Double? = null,
    val structuralHorizontalTilt: Double? = null,
    val structuralTiltSeverity: String? = null,
    val structuralTiltConfidence: Float? = null,
    val structuralLinesDetected: Int? = null,
    val structuralTiltWarning: String? = null,
    val locationName: String = "" // ✅ ADD THIS
)

data class DetailRecommendation(
    val title: String = "",
    val description: String = "",
    val severity: String = "",
    val actions: List<String> = emptyList(),
    val imageCount: Int = 0,  // NEW: Number of images with this issue
    val avgConfidence: Float = 0f  // NEW: Average AI confidence
)

data class DetailAreaData(
    val areaId: String = "",
    val areaName: String = "",
    val areaType: String = "",
    val areaRisk: String = "Low Risk",
    val structuralAnalysisEnabled: Boolean = false,
    val images: List<DetailImageAssessment> = emptyList(),
    val areaDescription: String = ""    // NEW
)

class AssessmentDetailsActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var currentAssessmentName: MutableState<String>
    private lateinit var currentBuildingType: MutableState<String>
    private lateinit var currentConstructionYear: MutableState<String>
    private lateinit var currentRenovationYear: MutableState<String>
    private lateinit var currentFloors: MutableState<String>
    private lateinit var currentMaterial: MutableState<String>
    private lateinit var currentFoundation: MutableState<String>
    private lateinit var currentEnvironment: MutableState<String>
    private lateinit var currentPreviousIssues: MutableState<ArrayList<String>>
    private lateinit var currentOccupancy: MutableState<String>
    private lateinit var currentEnvironmentalRisks: MutableState<ArrayList<String>>
    private lateinit var currentNotes: MutableState<String>

    private lateinit var currentAddress: MutableState<String>
    private lateinit var currentFootprintArea: MutableState<String>
    private lateinit var currentTypeOfConstruction: MutableState<ArrayList<String>>


    private val editBuildingInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data?.getBooleanExtra("UPDATED", false) == true) {
                lifecycleScope.launch {
                    try {
                        val userId = firebaseAuth.currentUser?.uid ?: return@launch
                        val assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: return@launch

                        val document = firestore.collection("users")
                            .document(userId)
                            .collection("assessments")
                            .document(assessmentId)
                            .get().await()

                        if (document.exists()) {
                            currentAssessmentName.value = document.getString("assessmentName") ?: ""
                            currentBuildingType.value = document.getString("buildingType") ?: ""
                            currentConstructionYear.value = document.getString("constructionYear") ?: ""
                            currentRenovationYear.value = document.getString("renovationYear") ?: ""
                            currentFloors.value = document.getString("floors") ?: ""
                            currentMaterial.value = document.getString("material") ?: ""
                            currentFoundation.value = document.getString("foundation") ?: ""
                            currentEnvironment.value = document.getString("environment") ?: ""
                            currentPreviousIssues.value = ArrayList((document.get("previousIssues") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                            currentOccupancy.value = document.getString("occupancy") ?: ""
                            currentEnvironmentalRisks.value = ArrayList((document.get("environmentalRisks") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                            currentNotes.value = document.getString("notes") ?: ""

                            currentAddress.value = document.getString("address") ?: ""
                            currentFootprintArea.value = document.getString("footprintArea") ?: ""
                            currentTypeOfConstruction.value = ArrayList((document.get("typeOfConstruction") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())

                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@AssessmentDetailsActivity, "Error updating data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        firebaseAuth = FirebaseAuth.getInstance()

        val assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: ""
        val title = intent.getStringExtra("ASSESSMENT_TITLE") ?: "Assessment"
        val date = intent.getStringExtra("ASSESSMENT_DATE") ?: ""
        val buildingType = intent.getStringExtra("BUILDING_TYPE") ?: ""
        val constructionYear = intent.getStringExtra("CONSTRUCTION_YEAR") ?: ""
        val renovationYear = intent.getStringExtra("RENOVATION_YEAR") ?: ""
        val floors = intent.getStringExtra("FLOORS") ?: ""
        val material = intent.getStringExtra("MATERIAL") ?: ""
        val foundation = intent.getStringExtra("FOUNDATION") ?: ""
        val environment = intent.getStringExtra("ENVIRONMENT") ?: ""
        val previousIssues: ArrayList<String>? = intent.getStringArrayListExtra("PREVIOUS_ISSUES")
        val occupancy = intent.getStringExtra("OCCUPANCY") ?: ""
        val environmentalRisks: ArrayList<String>? = intent.getStringArrayListExtra("ENVIRONMENTAL_RISKS")
        val notes = intent.getStringExtra("NOTES") ?: ""

        val address = intent.getStringExtra("ADDRESS") ?: ""
        val footprintArea = intent.getStringExtra("FOOTPRINT_AREA") ?: ""
        val typeOfConstruction: ArrayList<String>? = intent.getStringArrayListExtra("TYPE_OF_CONSTRUCTION")

        currentAssessmentName = mutableStateOf(title)
        currentBuildingType = mutableStateOf(buildingType)
        currentConstructionYear = mutableStateOf(constructionYear)
        currentRenovationYear = mutableStateOf(renovationYear)
        currentFloors = mutableStateOf(floors)
        currentMaterial = mutableStateOf(material)
        currentFoundation = mutableStateOf(foundation)
        currentEnvironment = mutableStateOf(environment)
        currentPreviousIssues = mutableStateOf(previousIssues ?: arrayListOf())
        currentOccupancy = mutableStateOf(occupancy)
        currentEnvironmentalRisks = mutableStateOf(environmentalRisks ?: arrayListOf())
        currentNotes = mutableStateOf(notes)

        currentAddress = mutableStateOf(address)
        currentFootprintArea = mutableStateOf(footprintArea)
        currentTypeOfConstruction = mutableStateOf(typeOfConstruction ?: arrayListOf())


        lifecycleScope.launch {
            try {
                val userId = firebaseAuth.currentUser?.uid ?: return@launch

                val document = firestore.collection("users")
                    .document(userId)
                    .collection("assessments")
                    .document(assessmentId)
                    .get().await()

                if (document.exists()) {
                    currentAssessmentName.value = document.getString("assessmentName") ?: title
                    currentBuildingType.value = document.getString("buildingType") ?: ""
                    currentConstructionYear.value = document.getString("constructionYear") ?: ""
                    currentRenovationYear.value = document.getString("renovationYear") ?: ""
                    currentFloors.value = document.getString("floors") ?: ""
                    currentMaterial.value = document.getString("material") ?: ""
                    currentFoundation.value = document.getString("foundation") ?: ""
                    currentEnvironment.value = document.getString("environment") ?: ""
                    currentPreviousIssues.value = ArrayList((document.get("previousIssues") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                    currentOccupancy.value = document.getString("occupancy") ?: ""
                    currentEnvironmentalRisks.value = ArrayList((document.get("environmentalRisks") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                    currentNotes.value = document.getString("notes") ?: ""

                    currentAddress.value = document.getString("address") ?: ""
                    currentFootprintArea.value = document.getString("footprintArea") ?: ""
                    currentTypeOfConstruction.value = ArrayList((document.get("typeOfConstruction") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())

                }
            } catch (e: Exception) {
                Log.e("AssessmentDetails", "Error fetching fresh data", e)
            }
        }

        setContent {
            MaterialTheme {
                AssessmentDetailsScreen(
                    assessmentId = assessmentId,
                    title = currentAssessmentName.value,
                    date = date,
                    buildingType = currentBuildingType.value,
                    constructionYear = currentConstructionYear.value,
                    renovationYear = currentRenovationYear.value,
                    floors = currentFloors.value,
                    material = currentMaterial.value,
                    foundation = currentFoundation.value,
                    environment = currentEnvironment.value,
                    previousIssues = currentPreviousIssues.value,
                    occupancy = currentOccupancy.value,
                    environmentalRisks = currentEnvironmentalRisks.value,
                    notes = currentNotes.value,
                    address = currentAddress.value,
                    footprintArea = currentFootprintArea.value,
                    typeOfConstruction = currentTypeOfConstruction.value,
                    getRecommendation = ::getRecommendation,
                    onEditBuildingInfo = {
                        val intent = Intent(this@AssessmentDetailsActivity, EditBuildingInfoActivity::class.java).apply {
                            putExtra("assessmentName", currentAssessmentName.value)
                            putExtra(IntentKeys.BUILDING_TYPE, currentBuildingType.value)
                            putExtra(IntentKeys.CONSTRUCTION_YEAR, currentConstructionYear.value)
                            putExtra(IntentKeys.RENOVATION_YEAR, currentRenovationYear.value)
                            putExtra(IntentKeys.FLOORS, currentFloors.value)
                            putExtra(IntentKeys.MATERIAL, currentMaterial.value)
                            putExtra(IntentKeys.FOUNDATION, currentFoundation.value)
                            putExtra(IntentKeys.ENVIRONMENT, currentEnvironment.value)
                            putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, currentPreviousIssues.value)
                            putExtra(IntentKeys.OCCUPANCY, currentOccupancy.value)
                            putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, currentEnvironmentalRisks.value)
                            putExtra(IntentKeys.NOTES, currentNotes.value)
                            putExtra("ASSESSMENT_ID", assessmentId)

                            putExtra(IntentKeys.ADDRESS, currentAddress.value)
                            putExtra(IntentKeys.FOOTPRINT_AREA, currentFootprintArea.value)
                            putStringArrayListExtra(IntentKeys.TYPE_OF_CONSTRUCTION, currentTypeOfConstruction.value)
                        }
                        editBuildingInfoLauncher.launch(intent)
                    }
                )
            }
        }
    }

    fun getRecommendation(damageType: String, damageLevel: String): DamageRecommendation {
        val key = "$damageType-$damageLevel"
        return when (key) {
            "Spalling-High" -> DamageRecommendation(
                title = "Serious Concrete Damage",
                description = "Concrete is breaking away from the surface, possibly exposing metal bars inside. This needs urgent attention from a building expert.",
                severity = "HIGH",
                actions = listOf(
                    "Call a structural engineer or building expert within 2-3 days",
                    "Take clear photos of the damaged area from different angles",
                    "Check if you can see any metal bars (rebar) showing through - avoid using this area",
                    "Measure the damage - if deeper than 1 inch or larger than your hand, it needs professional repair",
                    "Professional will remove damaged concrete, clean metal bars, fill with repair cement",
                    "After repair, seal the surface to protect it from water and prevent future damage"
                ),
                severityColor = Color(0xFFD32F2F),
                severityBgColor = Color(0xFFFFEBEE)
            )
            "Major Crack-High" -> DamageRecommendation(
                title = "Large Crack Found",
                description = "Wide crack detected (wider than 3mm or about 1/8 inch). This could mean the foundation is settling or the structure is under stress.",
                severity = "HIGH",
                actions = listOf(
                    "Contact a structural engineer or building expert within 1-2 weeks",
                    "Put markers on both sides of the crack to see if it's getting bigger",
                    "Measure and photograph the crack - note how wide, how long, and where it is",
                    "Check if doors or windows are sticking, or if floors are sloping",
                    "Expert may inject special material to fill the crack or strengthen the structure",
                    "Seal the crack after repair to keep water out and prevent freeze damage"
                ),
                severityColor = Color(0xFFD32F2F),
                severityBgColor = Color(0xFFFFEBEE)
            )
            "Minor Crack-Low" -> DamageRecommendation(
                title = "Small Hairline Cracks",
                description = "Thin cracks found - these are common as buildings settle and concrete dries. Usually not serious, but keep an eye on them.",
                severity = "LOW",
                actions = listOf(
                    "Check these cracks once or twice a year during regular building inspections",
                    "Watch if the crack gets bigger over 6-12 months - mark the ends and take photos with a ruler",
                    "Fill the cracks during your next scheduled maintenance to stop water getting in",
                    "Use flexible crack filler that works for indoor or outdoor use",
                    "No need to worry - these small cracks are normal in concrete and brick buildings"
                ),
                severityColor = Color(0xFF388E3C),
                severityBgColor = Color(0xFFE8F5E9)
            )
            "Paint Damage-Low" -> DamageRecommendation(
                title = "Paint Peeling or Flaking",
                description = "Paint is coming off the surface. Usually caused by water damage or old paint. Mostly cosmetic, but fix the water source first.",
                severity = "LOW",
                actions = listOf(
                    "Plan to repaint within 12-24 months during regular maintenance",
                    "Find and fix the water problem FIRST (look for leaks, bad drainage, or too much humidity)",
                    "Proper fix steps: scrape off loose paint, clean the surface, apply primer, then paint",
                    "Choose the right paint - mildew-resistant for bathrooms/kitchens, weather-resistant for outside",
                    "This is a cosmetic issue - no safety concerns, just maintenance needed"
                ),
                severityColor = Color(0xFF388E3C),
                severityBgColor = Color(0xFFE8F5E9)
            )
            "Algae-Moderate" -> DamageRecommendation(
                title = "Algae/Moss Growth",
                description = "Algae or moss growing on the building means there's too much moisture. Not immediately dangerous, but can damage materials over time.",
                severity = "MODERATE",
                actions = listOf(
                    "Clean the area within 1-2 months using algae remover or cleaning solution",
                    "Cleaning method: gently wash with garden hose and soft brush - DON'T use pressure washer",
                    "Find and fix why it's wet (improve drainage, fix gutters, repair any roof leaks)",
                    "Cut back trees and bushes so more sunlight reaches the wall and air can flow",
                    "You can apply special coating to prevent algae from growing back",
                    "Check again in 6-12 months to make sure the moisture problem is fixed"
                ),
                severityColor = Color(0xFFF57C00),
                severityBgColor = Color(0xFFFFF3E0)
            )
            else -> DamageRecommendation(
                title = "Clean Surface",
                description = "No structural damage or surface deterioration detected. Building surface appears well-maintained and in good condition.",
                severity = "GOOD",
                actions = listOf(
                    "Continue regular maintenance schedule (annual or bi-annual inspections)",
                    "Monitor during routine inspections for any emerging issues",
                    "Maintain proper drainage and moisture control measures",
                    "No immediate action required - building surface in good condition"
                ),
                severityColor = Color(0xFF2E7D32),
                severityBgColor = Color(0xFFE8F5E9)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentDetailsScreen(
    assessmentId: String,
    title: String,
    date: String,
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

    address: String = "",
    footprintArea: String = "",
    typeOfConstruction: List<String> = emptyList(),

    getRecommendation: (String, String) -> DamageRecommendation,
    onEditBuildingInfo: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var areaData by remember { mutableStateOf(listOf<DetailAreaData>()) }
    var isLoadingData by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }
    var overallRisk by remember { mutableStateOf("Low Risk") }
    var totalIssues by remember { mutableStateOf(0) }

    LaunchedEffect(assessmentId) {
        if (assessmentId.isNotEmpty()) {
            isLoadingData = true
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val userId = currentUser.uid
                    val firestore = FirebaseFirestore.getInstance()
                    val document = firestore.collection("users")
                        .document(userId)
                        .collection("assessments")
                        .document(assessmentId)
                        .get()
                        .await()

                    if (document.exists()) {
                        overallRisk = document.getString("overallRisk") ?: "Low Risk"
                        totalIssues = (document.getLong("totalIssues") ?: 0).toInt()

                        val areasRaw = document.get("areas") as? List<HashMap<String, Any>>
                        if (areasRaw != null) {
                            areaData = areasRaw.map { areaMap ->
                                val imagesRaw = areaMap["images"] as? List<*>
                                val images = imagesRaw?.mapNotNull { imgItem ->
                                    if (imgItem is Map<*, *>) {
                                        val detectedIssuesRaw = imgItem["detectedIssues"] as? List<*>
                                        val detectedIssues = detectedIssuesRaw?.mapNotNull { issueItem ->
                                            if (issueItem is Map<*, *>) {
                                                DetailDetectedIssue(
                                                    damageType = issueItem["type"] as? String ?: "Unknown",
                                                    damageLevel = issueItem["level"] as? String ?: "Low",
                                                    confidence = (issueItem["confidence"] as? Number)?.toFloat() ?: 0f
                                                )
                                            } else null
                                        } ?: emptyList()

                                        val recsRaw = imgItem["recommendations"] as? List<*>
                                        val recs = recsRaw?.mapNotNull { recItem ->
                                            if (recItem is Map<*, *>) {
                                                DetailRecommendation(
                                                    title = recItem["title"] as? String ?: "",
                                                    description = recItem["description"] as? String ?: "",
                                                    severity = recItem["severity"] as? String ?: "",
                                                    actions = (recItem["actions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                                    imageCount = (recItem["imageCount"] as? Number)?.toInt() ?: 0,  // ✅ ADD THIS
                                                    avgConfidence = (recItem["avgConfidence"] as? Number)?.toFloat() ?: 0f  // ✅ ADD THIS
                                                )
                                            } else null
                                        } ?: emptyList()

                                        DetailImageAssessment(
                                            imageUrl = imgItem["imageUri"] as? String ?: "",
                                            locationName = imgItem["locationName"] as? String ?: "",
                                            detectedIssues = detectedIssues,
                                            imageRisk = imgItem["imageRisk"] as? String ?: "Low",
                                            plainConf = (imgItem["plainConf"] as? Number)?.toFloat() ?: 0f,
                                            recommendations = recs,
                                            structuralVerticalTilt = (imgItem["structuralVerticalTilt"] as? Number)?.toDouble(),
                                            structuralHorizontalTilt = (imgItem["structuralHorizontalTilt"] as? Number)?.toDouble(),
                                            structuralTiltSeverity = imgItem["structuralTiltSeverity"] as? String,
                                            structuralTiltConfidence = (imgItem["structuralTiltConfidence"] as? Number)?.toFloat(),
                                            structuralLinesDetected = (imgItem["structuralLinesDetected"] as? Number)?.toInt(),
                                            structuralTiltWarning = imgItem["structuralTiltWarning"] as? String
                                        )
                                    } else null
                                } ?: emptyList()

                                DetailAreaData(
                                    areaId = areaMap["areaId"] as? String ?: "",
                                    areaName = areaMap["areaName"] as? String ?: "",
                                    areaType = areaMap["areaType"] as? String ?: "",
                                    areaRisk = areaMap["areaRisk"] as? String ?: "Low Risk",
                                    structuralAnalysisEnabled = areaMap["structuralAnalysisEnabled"] as? Boolean ?: false,
                                    images = images,
                                    areaDescription = areaMap["areaDescription"] as? String ?: ""  // NEW
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AssessmentDetails", "Error loading data", e)
            } finally {
                isLoadingData = false
            }
        }
    }

    val riskColor = when (overallRisk) {
        "High Risk" -> Color(0xFFD32F2F)
        "Moderate Risk" -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }

    val riskBgColor = when (overallRisk) {
        "High Risk" -> Color(0xFFFFEBEE)
        "Moderate Risk" -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }

    val allImages = areaData.flatMap { it.images }
    val allDetectedIssues = allImages.flatMap { it.detectedIssues }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ✅ TOP BAR - Matches AssessmentResultsActivity
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
                        val intent = Intent(context, HistoryActivity::class.java)
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

        // ✅ SCROLLABLE CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ✅ ASSESSMENT NAME - Centered
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = date,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ✅ OVERALL RISK CARD - Matches AssessmentResultsActivity
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
                            text = overallRisk,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = buildString {
                            append("Analysis of ${allImages.size} ")
                            append(if (allImages.size == 1) "photo" else "photos")
                            append(" completed. ")
                            if (totalIssues > 0) {
                                append("$totalIssues ")
                                append(if (totalIssues == 1) "area" else "areas")
                                append(" of concern detected.")
                            } else {
                                append("No significant issues detected.")
                            }
                        },
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ BUILDING INFORMATION CARD - With Icon Badge
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

                address = address,
                footprintArea = footprintArea,
                typeOfConstruction = typeOfConstruction,

                assessmentName = title,
                onEditClick = onEditBuildingInfo,
                initiallyExpanded = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ DETECTION SUMMARY - With Icon Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                border = BorderStroke(1.dp, Color(0xFFE5E5E5))
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

                        if (allDetectedIssues.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = "${totalIssues} ${if (totalIssues == 1) "Issue" else "Issues"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (allDetectedIssues.isEmpty()) {
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
                        val groupedIssues = allDetectedIssues.groupBy { it.damageType }
                        groupedIssues.forEach { (damageType, issues) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))
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
                                        val issueColor = when {
                                            damageType.contains("Spalling") || damageType.contains("Major Crack") -> Color(0xFFFFEBEE)
                                            damageType.contains("Algae") -> Color(0xFFFFF3E0)
                                            else -> Color(0xFFF0FDF4)
                                        }

                                        val textColor = when {
                                            damageType.contains("Spalling") || damageType.contains("Major Crack") -> Color(0xFFDC2626)
                                            damageType.contains("Algae") -> Color(0xFFF59E0B)
                                            else -> Color(0xFF16A34A)
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = issueColor,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = issues.size.toString(),
                                                    color = textColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = damageType,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black
                                            )
                                            val avgConf = (issues.map { it.confidence }.average() * 100).toInt()
                                            Text(
                                                text = "Avg Confidence: $avgConf%",
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

            Spacer(modifier = Modifier.height(20.dp))

            // ✅ WALL & FLOOR TILT CHECK - With Icon Badge
            // ✅ WALL & FLOOR TILT CHECK - Matching AssessmentResultsActivity design
            val hasStructuralAnalysis = areaData.any { it.structuralAnalysisEnabled }
            if (hasStructuralAnalysis) {
                var isTiltExpanded by remember { mutableStateOf(true) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E5E5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ✅ HEADER - Icon on left, title next to it
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                text = "Wall & Floor Tilt Check",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ✅ WARNING CARD
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
                                    "This is a rough estimate from your phone camera. For accurate measurements, hire a professional with proper surveying tools.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ✅ Calculate tilt counts
                        val imagesWithTilt = allImages.filter {
                            it.structuralTiltSeverity != null && it.structuralTiltSeverity != "NONE"
                        }
                        val severeCount = imagesWithTilt.count {
                            it.structuralTiltSeverity == "SEVERE" || it.structuralTiltSeverity == "CRITICAL"
                        }
                        val moderateCount = imagesWithTilt.count {
                            it.structuralTiltSeverity == "MODERATE"
                        }
                        val minorCount = imagesWithTilt.count {
                            it.structuralTiltSeverity == "MINOR"
                        }
                        val totalImages = allImages.size
                        val hasStructuralIssues = (severeCount + moderateCount + minorCount) > 0

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
                                    "Walls and floors look level",
                                    fontSize = 14.sp,
                                    color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                "Found tilt in ${severeCount + moderateCount + minorCount} of $totalImages checked photos",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            Spacer(Modifier.height(12.dp))

                            // ✅ SIMPLIFIED SEVERITY ROWS
                            if (severeCount > 0) {
                                TiltQualityRow(
                                    label = "Serious slant (needs expert)",
                                    description = "Walls or floors leaning more than 5°",
                                    count = severeCount,
                                    percent = (severeCount * 100f / totalImages).toInt(),
                                    color = Color(0xFFEF4444)
                                )
                            }

                            if (moderateCount > 0) {
                                TiltQualityRow(
                                    label = "Noticeable tilt",
                                    description = "Tilted 2-5° - worth checking",
                                    count = moderateCount,
                                    percent = (moderateCount * 100f / totalImages).toInt(),
                                    color = Color(0xFFF59E0B)
                                )
                            }

                            if (minorCount > 0) {
                                TiltQualityRow(
                                    label = "Slight tilt",
                                    description = "Small tilt under 2° - usually normal",
                                    count = minorCount,
                                    percent = (minorCount * 100f / totalImages).toInt(),
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ✅ ADD THIS COMPOSABLE FUNCTION (if not already present in AssessmentDetailsActivity)
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


            // ✅ AREA-BY-AREA ANALYSIS - With Icon Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Area-by-Area Analysis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            text = "${areaData.size} ${if (areaData.size == 1) "area" else "areas"} analyzed",
                            fontSize = 14.sp,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ AREA CARDS
            areaData.forEach { area ->
                AreaDetailsCard(
                    area = area,
                    onImageClick = {
                        selectedImageUrl = it
                        showImageViewer = true
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ✅ BOTTOM ACTION BUTTONS - Matches AssessmentResultsActivity
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDownloadDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF2196F3)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2196F3)
                        )
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Download",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val intent = Intent(context, HistoryActivity::class.java)
                            context.startActivity(intent)
                            (context as? ComponentActivity)?.finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Back to History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Image Viewer Dialog
    if (showImageViewer && selectedImageUrl != null) {
        Dialog(
            onDismissRequest = { showImageViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showImageViewer = false }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(selectedImageUrl),
                    contentDescription = "Full image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { showImageViewer = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    // ✅ FIXED Download Dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download Report") },
            text = { Text("Generate and download a PDF report of this assessment?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadDialog = false
                        coroutineScope.launch {
                            try {
                                val imageUrls = allImages.map { it.imageUrl }

                                val pdfData = PdfAssessmentData(
                                    assessmentName = title,
                                    date = date,
                                    overallRisk = overallRisk,
                                    totalIssues = totalIssues,
                                    crackHighCount = allDetectedIssues.count {
                                        it.damageType == "Spalling" || it.damageType == "Major Crack"
                                    },
                                    crackModerateCount = 0,
                                    crackLowCount = allDetectedIssues.count {
                                        it.damageType == "Minor Crack"
                                    },
                                    paintCount = allDetectedIssues.count {
                                        it.damageType == "Paint Damage"
                                    },
                                    algaeCount = allDetectedIssues.count {
                                        it.damageType == "Algae"
                                    },
                                    buildingType = buildingType,
                                    constructionYear = constructionYear,
                                    renovationYear = renovationYear,
                                    floors = floors,
                                    material = material,
                                    foundation = foundation,
                                    environment = environment,
                                    previousIssues = previousIssues.joinToString(", "),
                                    occupancy = occupancy,
                                    environmentalRisks = environmentalRisks.joinToString(", "),
                                    notes = notes,
                                    imageUrls = imageUrls
                                )

                                val pdfPath = PdfReportGenerator.generatePdfReport(context, pdfData)

                                if (pdfPath != null) {
                                    Toast.makeText(
                                        context,
                                        "PDF saved to Downloads folder!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to generate PDF",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ✅ AREA DETAILS CARD - Matches AssessmentResultsActivity
@Composable
fun AreaDetailsCard(
    area: DetailAreaData,
    onImageClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    val areaIcon = when {
        area.areaType.contains("FOUNDATION") -> Icons.Default.Foundation
        area.areaType.contains("WALL") -> Icons.Default.CropSquare
        area.areaType.contains("COLUMN") -> Icons.Default.ViewColumn
        area.areaType.contains("ROOF") -> Icons.Default.Roofing
        else -> Icons.Default.Home
    }

    val areaRiskColor = when {
        area.areaRisk.contains("High") -> Color(0xFFD32F2F)
        area.areaRisk.contains("Moderate") -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }

    val areaHeaderBackgroundColor = when {
        area.areaRisk.contains("High") -> Color(0xFFFFEBEE)
        area.areaRisk.contains("Moderate") -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, areaRiskColor.copy(alpha = 0.3f))
    ) {
        Column {
            // ===== IMPROVED HEADER SECTION =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(areaHeaderBackgroundColor)
                    .clickable { isExpanded = !isExpanded }
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
                        text = area.areaName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        lineHeight = 22.sp,
                        maxLines = 1
                    )

                    // Description (if exists) - wraps fully
                    if (area.areaDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = area.areaDescription,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B), // Slate gray for better contrast
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
                            text = if (area.images.size == 1)
                                "1 photo analyzed"
                            else
                                "${area.images.size} photos analyzed",
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
                            text = area.areaRisk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = areaRiskColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ===== CONTENT SECTION (remains the same) =====
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (area.structuralAnalysisEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Architecture,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Structural tilt analysis enabled for this area",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "Image Analysis",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    area.images.forEachIndexed { index, image ->
                        ImageAnalysisCard(
                            imageAssessment = image,
                            imageNumber = index + 1,
                            onImageClick = onImageClick
                        )

                        if (index < area.images.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Recommendations Section
                    val areaIssueGroups = mutableMapOf<String, MutableList<DetailDetectedIssue>>()
                    area.images.forEach { image ->
                        image.detectedIssues.forEach { issue ->
                            val key = "${issue.damageType}-${issue.damageLevel}"
                            areaIssueGroups.getOrPut(key) { mutableListOf() }.add(issue)
                        }
                    }

                    // FIXED: Single header for recommendations
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recommended Actions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (areaIssueGroups.isEmpty()) {
                        val cleanRec = DetailRecommendation(
                            title = "Clean Surface",
                            description = "No structural damage or surface deterioration detected. Building surface appears well-maintained and in good condition.",
                            severity = "GOOD",
                            actions = listOf(
                                "Continue regular maintenance schedule (annual or bi-annual inspections)",
                                "Monitor during routine inspections for any emerging issues",
                                "Maintain proper drainage and moisture control measures",
                                "No immediate action required - building surface in good condition"
                            ),
                            imageCount = area.images.size,
                            avgConfidence = 0f
                        )
                        RecommendationCard(recommendation = cleanRec)
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        val context = LocalContext.current
                        val activity = context as? AssessmentDetailsActivity

                        areaIssueGroups.forEach { (_, issuesList) ->
                            val firstIssue = issuesList.first()
                            val avgConf = issuesList.map { it.confidence }.average().toFloat()
                            val imageCount = issuesList.size

                            val baseRec = activity?.getRecommendation(firstIssue.damageType, firstIssue.damageLevel)

                            if (baseRec != null) {
                                val rec = DetailRecommendation(
                                    title = baseRec.title,
                                    description = baseRec.description,
                                    severity = baseRec.severity,
                                    actions = baseRec.actions,
                                    imageCount = imageCount,
                                    avgConfidence = avgConf
                                )
                                RecommendationCard(recommendation = rec)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageAnalysisCard(
    imageAssessment: DetailImageAssessment,
    imageNumber: Int,
    onImageClick: (String) -> Unit
) {
    val displayName = imageAssessment.locationName.ifEmpty { "Image $imageNumber" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ✅ IMAGE ON LEFT
            Image(
                painter = rememberAsyncImagePainter(imageAssessment.imageUrl),
                contentDescription = displayName,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(imageAssessment.imageUrl) },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // ✅ CONTENT ON RIGHT
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Location Name + Risk Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (imageAssessment.locationName.isNotEmpty())
                                Icons.Default.LocationOn
                            else
                                Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            displayName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    val riskColor = when (imageAssessment.imageRisk) {
                        "High" -> Color(0xFFD32F2F)
                        "Moderate" -> Color(0xFFF57C00)
                        else -> Color(0xFF388E3C)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = riskColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = imageAssessment.imageRisk.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tilt (if exists)
                imageAssessment.structuralTiltSeverity?.let { severity ->
                    if (severity != "NONE") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Architecture,
                                contentDescription = null,
                                tint = when (severity) {
                                    "SEVERE", "CRITICAL" -> Color(0xFFEF4444)
                                    "MODERATE" -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tilt: $severity ${imageAssessment.structuralVerticalTilt?.let { String.format("%.0f°", it) } ?: ""}",
                                fontSize = 12.sp,
                                color = when (severity) {
                                    "SEVERE", "CRITICAL" -> Color(0xFFEF4444)
                                    "MODERATE" -> Color(0xFFF59E0B)
                                    else -> Color(0xFF3B82F6)
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Damage Type
                if (imageAssessment.detectedIssues.isEmpty()) {
                    Text(
                        text = "No damage detected",
                        fontSize = 13.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    imageAssessment.detectedIssues.forEach { issue ->
                        Text(
                            text = issue.damageType,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Confidence
                if (imageAssessment.detectedIssues.isNotEmpty()) {
                    imageAssessment.detectedIssues.forEach { issue ->
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
// ✅ RECOMMENDATION CARD
@Composable
fun RecommendationCard(recommendation: DetailRecommendation) {
    val severityColor = when (recommendation.severity) {
        "HIGH" -> Color(0xFFDC2626)
        "MODERATE" -> Color(0xFFF59E0B)
        "LOW" -> Color(0xFF16A34A)
        "GOOD" -> Color(0xFF16A34A)
        else -> Color(0xFF16A34A)
    }

    val severityBgColor = when (recommendation.severity) {
        "HIGH" -> Color(0xFFFFEEEE)
        "MODERATE" -> Color(0xFFFFF3E0)
        "LOW" -> Color(0xFFF0FDF4)
        "GOOD" -> Color(0xFFF0FDF4)
        else -> Color(0xFFF0FDF4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E5E5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title and Severity Badge
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

                    // Metadata: Image count and AI confidence
                    if (recommendation.imageCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${recommendation.imageCount} image${if (recommendation.imageCount > 1) "s" else ""} detected • AI Confidence: ${(recommendation.avgConfidence * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = severityBgColor
                ) {
                    Text(
                        text = recommendation.severity,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description Box
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

            // Action Steps
            recommendation.actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (index < recommendation.actions.size - 1) 10.dp else 0.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Numbered Circle Badge
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

                    // Action Text
                    Text(
                        text = action,
                        fontSize = 13.sp,
                        color = Color(0xFF333333),
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
