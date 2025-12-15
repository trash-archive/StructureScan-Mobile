package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.Preview
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
import java.util.*

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
    val crackLowConf: Float = 0f,
    val crackModerateConf: Float = 0f,
    val crackHighConf: Float = 0f,
    val paintConf: Float = 0f,
    val algaeConf: Float = 0f,
    val plainConf: Float = 0f,
    val recommendations: List<DetailRecommendation> = emptyList()  // ✅ ADD THIS
)

data class DetailRecommendation(
    val title: String = "",
    val description: String = "",
    val severity: String = "",
    val actions: List<String> = emptyList()
)

class AssessmentDetailsActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth

    // ✅ Mutable states
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

    // ✅ Launcher
    private val editBuildingInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data?.getBooleanExtra("UPDATED", false) == true) {
                lifecycleScope.launch {
                    try {
                        val userId = firebaseAuth.currentUser?.uid ?: return@launch
                        val updatedName = data.getStringExtra("assessmentName") ?: return@launch

                        val documents = firestore.collection("users")
                            .document(userId)
                            .collection("assessments")
                            .whereEqualTo("assessmentName", updatedName)
                            .get().await()

                        if (!documents.isEmpty) {
                            val doc = documents.documents[0]
                            currentAssessmentName.value = doc.getString("assessmentName") ?: ""
                            currentBuildingType.value = doc.getString("buildingType") ?: ""
                            currentConstructionYear.value = doc.getString("constructionYear") ?: ""
                            currentRenovationYear.value = doc.getString("renovationYear") ?: ""
                            currentFloors.value = doc.getString("floors") ?: ""
                            currentMaterial.value = doc.getString("material") ?: ""
                            currentFoundation.value = doc.getString("foundation") ?: ""
                            currentEnvironment.value = doc.getString("environment") ?: ""
                            currentPreviousIssues.value = ArrayList((doc.get("previousIssues") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                            currentOccupancy.value = doc.getString("occupancy") ?: ""
                            currentEnvironmentalRisks.value = ArrayList((doc.get("environmentalRisks") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                            currentNotes.value = doc.getString("notes") ?: ""

                            Toast.makeText(this@AssessmentDetailsActivity, "✓ Building info updated", Toast.LENGTH_SHORT).show()
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
        val riskLevel = intent.getStringExtra("RISK_LEVEL") ?: "Low Risk"
        val issuesCount = intent.getIntExtra("ISSUES_COUNT", 0)
        val crackHigh = intent.getIntExtra("CRACK_HIGH", 0)
        val crackModerate = intent.getIntExtra("CRACK_MODERATE", 0)
        val crackLow = intent.getIntExtra("CRACK_LOW", 0)
        val paintCount = intent.getIntExtra("PAINT_COUNT", 0)    // ✅
        val algaeCount = intent.getIntExtra("ALGAE_COUNT", 0)    // ✅

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

        // ✅ Initialize mutable states
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

        // ✅✅✅ ADD THIS HERE - Fetch fresh data from Firestore
        lifecycleScope.launch {
            try {
                val userId = firebaseAuth.currentUser?.uid ?: return@launch

                val document = firestore.collection("users")
                    .document(userId)
                    .collection("assessments")
                    .document(assessmentId)
                    .get().await()

                if (document.exists()) {
                    // Update all states with FRESH Firestore data
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
                }
            } catch (e: Exception) {
                // Silently fail - intent data is already loaded as fallback
            }
        }

        setContent {
            MaterialTheme {
                AssessmentDetailsScreen(
                    assessmentId = assessmentId,
                    title = currentAssessmentName.value,
                    date = date,
                    riskLevel = riskLevel,
                    issuesCount = issuesCount,
                    crackHigh = crackHigh,
                    crackModerate = crackModerate,
                    crackLow = crackLow,
                    paintCount = paintCount,    // ✅ Single merged count
                    algaeCount = algaeCount,
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
                    getRecommendation = ::getRecommendation,
                    // ✅ ADD THIS:
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
                        }
                        editBuildingInfoLauncher.launch(intent)  // ← No cast needed now!
                    }
                )
            }
        }
    }

    private fun getRecommendation(damageType: String, damageLevel: String): DamageRecommendation {
        val key = "$damageType-$damageLevel"
        return when (key) {
            // ✅ UPDATED: Spalling (was Crack-High)
            "Spalling-High" -> DamageRecommendation(
                title = "Serious Concrete Damage",
                description = "Concrete is breaking away from the surface, possibly exposing metal bars inside. This needs urgent attention from a building expert.",
                severity = "HIGH",
                actions = listOf(
                    "Call a structural engineer or building expert within 2-3 days",
                    "Take clear photos of the damaged area from different angles",
                    "Check if you can see any metal bars (rebar) showing through - avoid using this area",
                    "Measure the damage - if deeper than 1 inch or larger than your hand, it needs professional repair",
                    "Tap around the area gently - if it sounds hollow, more concrete might be loose",
                    "Look for what's causing it: water leaks, cracks, or drainage problems",
                    "Professional will: remove damaged concrete, clean metal bars, fill with repair cement",
                    "After repair: seal the surface to protect it from water and prevent future damage"
                ),
                severityColor = Color(0xFFD32F2F),
                severityBgColor = Color(0xFFFFEBEE)
            )

            // ✅ UPDATED: Major Crack (was Crack-Moderate)
            "Major Crack-High" -> DamageRecommendation(
                title = "Large Crack Found",
                description = "Wide crack detected (wider than 3mm or about 1/8 inch). This could mean the foundation is settling or the structure is under stress. Get a building expert to check it out.",
                severity = "HIGH",
                actions = listOf(
                    "Contact a structural engineer or building expert within 1-2 weeks",
                    "Put markers on both sides of the crack to see if it's getting bigger",
                    "Measure and photograph the crack - note how wide, how long, and where it is",
                    "Check if doors or windows are sticking, or if floors are sloping",
                    "Look for water problems: check gutters, downspouts, and drainage around your building",
                    "Notice the crack direction: straight up (settling), sideways (pressure), or diagonal (twisting)",
                    "Expert may inject special material to fill the crack or strengthen the structure",
                    "Fix the root cause: improve drainage, stabilize foundation, or reduce soil pressure",
                    "Seal the crack after repair to keep water out and prevent freeze damage"
                ),
                severityColor = Color(0xFFD32F2F),
                severityBgColor = Color(0xFFFFEBEE)
            )

            // ✅ UPDATED: Minor Crack (was Crack-Low)
            "Minor Crack-Low" -> DamageRecommendation(
                title = "Small Hairline Crack/s",
                description = "Thin cracks found - these are common as buildings settle and concrete dries. Usually not serious, but keep an eye on them.",
                severity = "LOW",
                actions = listOf(
                    "Check these cracks once or twice a year during regular building inspections",
                    "Watch if the crack gets bigger over 6-12 months - mark the ends and take photos with a ruler",
                    "Fill the cracks during your next scheduled maintenance to stop water getting in",
                    "Use flexible crack filler that works for indoor or outdoor use",
                    "Make sure water drains properly away from your building",
                    "If the crack grows wider than 2mm (about 1/16 inch), call a building expert",
                    "Keep notes and photos of where the crack is and what it looks like",
                    "No need to worry - these small cracks are normal in concrete and brick buildings"
                ),
                severityColor = Color(0xFF388E3C),
                severityBgColor = Color(0xFFE8F5E9)
            )

            // ✅ UPDATED: Paint Damage (was Paint-Detected, now LOW risk)
            "Paint Damage-Low" -> DamageRecommendation(
                title = "Paint Peeling or Flaking",
                description = "Paint is coming off the surface. Usually caused by water damage or old paint. Mostly cosmetic, but fix the water source first to prevent it from happening again.",
                severity = "LOW",
                actions = listOf(
                    "Plan to repaint within 12-24 months during regular maintenance",
                    "Find and fix the water problem FIRST: look for leaks, bad drainage, or too much humidity",
                    "Proper fix steps: scrape off loose paint, clean the surface, apply primer, then paint",
                    "Make sure the surface is completely dry before repainting",
                    "Choose the right paint: mildew-resistant for bathrooms/kitchens, weather-resistant for outside",
                    "Add better airflow in damp areas (install fans or open windows more often)",
                    "For outside: keep gutters clean, make sure wood isn't touching the ground",
                    "Use bonding primer so new paint sticks properly",
                    "Seal gaps and joints with good quality sealant after painting",
                    "This is a cosmetic issue - no safety concerns, just maintenance needed"
                ),
                severityColor = Color(0xFF388E3C),
                severityBgColor = Color(0xFFE8F5E9)
            )

            // ✅ UPDATED: Algae (still MODERATE risk)
            "Algae-Moderate" -> DamageRecommendation(
                title = "Algae/Moss Growth",
                description = "Algae or moss growing on the building means there's too much moisture. Not immediately dangerous, but can damage materials over time if you don't clean it and fix the water problem.",
                severity = "MODERATE",
                actions = listOf(
                    "Clean the area within 1-2 months using algae remover or cleaning solution",
                    "Cleaning method: gently wash with garden hose and soft brush - DON'T use pressure washer on delicate surfaces",
                    "Cleaning solutions you can use: bleach mixed with water (50/50) OR vinegar solution (2 gallons water + 2-3 cups white vinegar)",
                    "Let the cleaning solution sit for 15-20 minutes, gently scrub, then rinse well",
                    "Find and fix why it's wet: improve drainage, fix gutters, repair any roof leaks",
                    "Cut back trees and bushes so more sunlight reaches the wall and air can flow",
                    "Make sure ground slopes away from building so water runs off",
                    "You can apply special coating to prevent algae from growing back",
                    "Check again in 6-12 months to make sure the moisture problem is fixed",
                    "If algae keeps coming back, you may need to seal the surface with breathable, water-repellent coating"
                ),
                severityColor = Color(0xFFF57C00),
                severityBgColor = Color(0xFFFFF3E0)
            )

            // ✅ Default fallback
            else -> DamageRecommendation(
                title = "Clean Surface",
                description = "No structural damage or surface deterioration detected. Building surface appears well-maintained and in good condition. Continue routine preventive maintenance to preserve structural integrity.",
                severity = "GOOD",
                actions = listOf(
                    "Continue regular maintenance schedule (annual or bi-annual inspections)",
                    "Monitor during routine inspections for any emerging issues",
                    "Maintain proper drainage and moisture control measures",
                    "Keep gutters and downspouts clear and functional",
                    "Ensure vegetation is trimmed back from building surfaces",
                    "Address any new cracks, stains, or deterioration promptly",
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
    riskLevel: String,
    issuesCount: Int,
    crackHigh: Int,
    crackModerate: Int,
    crackLow: Int,
    paintCount: Int,      // ✅ Single merged count
    algaeCount: Int,      // ✅ Single merged count
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
    getRecommendation: (String, String) -> DamageRecommendation,
    onEditBuildingInfo: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var imageAssessments by remember { mutableStateOf(listOf<DetailImageAssessment>()) }
    var isLoadingImages by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }

    LaunchedEffect(assessmentId) {
        if (assessmentId.isNotEmpty()) {
            isLoadingImages = true
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
                        val assessmentsList = document.get("assessments") as? List<HashMap<String, Any>>
                        if (assessmentsList != null) {
                            imageAssessments = assessmentsList.map { assessmentMap ->
                                // ✅ CRITICAL: Parse detectedIssues array properly
                                val detectedIssuesRaw = assessmentMap["detectedIssues"] as? List<*>
                                val detectedIssues = detectedIssuesRaw?.mapNotNull { issueItem ->
                                    if (issueItem is Map<*, *>) {
                                        DetailDetectedIssue(
                                            damageType = issueItem["type"] as? String ?: "Unknown",
                                            damageLevel = issueItem["level"] as? String ?: "Low",
                                            confidence = (issueItem["confidence"] as? Number)?.toFloat() ?: 0f
                                        )
                                    } else {
                                        null
                                    }
                                } ?: emptyList()

                                // ✅ ADD THIS: Parse recommendations from Firestore
                                val recsRaw = assessmentMap["recommendations"] as? List<*>
                                val recs = recsRaw?.mapNotNull { recItem ->
                                    if (recItem is Map<*, *>) {
                                        DetailRecommendation(
                                            title = recItem["title"] as? String ?: "",
                                            description = recItem["description"] as? String ?: "",
                                            severity = recItem["severity"] as? String ?: "",
                                            actions = (recItem["actions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                                        )
                                    } else null
                                } ?: emptyList()

                                DetailImageAssessment(
                                    imageUrl = assessmentMap["imageUri"] as? String ?: "",
                                    detectedIssues = detectedIssues,  // ✅ NOW contains ALL issues!
                                    imageRisk = assessmentMap["imageRisk"] as? String ?: "Low",
                                    crackLowConf = (assessmentMap["crackLowConf"] as? Number)?.toFloat() ?: 0f,
                                    crackModerateConf = (assessmentMap["crackModerateConf"] as? Number)?.toFloat() ?: 0f,
                                    crackHighConf = (assessmentMap["crackHighConf"] as? Number)?.toFloat() ?: 0f,
                                    paintConf = (assessmentMap["paintConf"] as? Number)?.toFloat() ?: 0f,
                                    algaeConf = (assessmentMap["algaeConf"] as? Number)?.toFloat() ?: 0f,
                                    plainConf = (assessmentMap["plainConf"] as? Number)?.toFloat() ?: 0f,
                                    recommendations = recs  // ✅ ADD THIS
                                )
                            }

                            Log.d("AssessmentDetails", "Loaded ${imageAssessments.size} images")
                            imageAssessments.forEachIndexed { idx, img ->
                                Log.d("AssessmentDetails", "Image $idx has ${img.detectedIssues.size} issues")
                                img.detectedIssues.forEach { issue ->
                                    Log.d("AssessmentDetails", "  - ${issue.damageType}-${issue.damageLevel} @ ${issue.confidence}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AssessmentDetails", "Error loading images", e)
                e.printStackTrace()
            } finally {
                isLoadingImages = false
            }
        }
    }

    val riskColor = when (riskLevel) {
        "High Risk" -> Color(0xFFD32F2F)
        "Moderate Risk" -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        TopAppBar(
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val intent = Intent(context, HistoryActivity::class.java)
                            context.startActivity(intent)
                            (context as? ComponentActivity)?.finish()
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black
                            )
                        }
                        Text(
                            "Assessment Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0288D1)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    date,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, riskColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(riskColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        riskLevel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildString {
                        append("Analysis of ${imageAssessments.size} images completed. ")
                        if (issuesCount > 0) {
                            append("$issuesCount areas of concern detected.")
                        } else {
                            append("No significant issues detected.")
                        }
                    },
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))

            DetailsExpandableSection(
                title = "Building Information",
                trailingContent = {
                    IconButton(onClick = { onEditBuildingInfo() }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            ) {
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
                            // Show building info when available (your existing code)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (buildingType.isNotEmpty()) {
                                    InfoRow("Type", buildingType)
                                }
                                if (material.isNotEmpty()) {
                                    InfoRow("Material", material)
                                }
                                if (constructionYear.isNotEmpty()) {
                                    InfoRow("Built", constructionYear)
                                }
                                if (renovationYear.isNotEmpty()) {
                                    InfoRow("Renovated", renovationYear)
                                }
                                if (floors.isNotEmpty()) {
                                    InfoRow("Floors", floors)
                                }
                                if (foundation.isNotEmpty()) {
                                    InfoRow("Foundation", foundation)
                                }
                                if (environment.isNotEmpty()) {
                                    InfoRow("Environment", environment)
                                }
                                if (previousIssues.isNotEmpty()) {
                                    Column {
                                        Text(
                                            "Previous Issues",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        previousIssues.forEach { issue ->
                                            Text("• $issue", fontSize = 14.sp, color = Color.Black)
                                        }
                                    }
                                }
                                if (occupancy.isNotEmpty()) {
                                    InfoRow("Occupancy", occupancy)
                                }
                                if (environmentalRisks.isNotEmpty()) {
                                    Column {
                                        Text(
                                            "Environmental Risks",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        environmentalRisks.forEach { risk ->
                                            Text("• $risk", fontSize = 14.sp, color = Color.Black)
                                        }
                                    }
                                }
                                if (notes.isNotEmpty()) {
                                    Column {
                                        Text(
                                            "Additional Notes",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(notes, fontSize = 14.sp, color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Detection Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // ✅ FIX: Calculate counts from loaded imageAssessments
                    val allDetectedIssues = imageAssessments.flatMap { it.detectedIssues }
                    val spallingCount = allDetectedIssues.count { it.damageType == "Spalling" }
                    val majorCrackCount = allDetectedIssues.count { it.damageType == "Major Crack" }
                    val minorCrackCount = allDetectedIssues.count { it.damageType == "Minor Crack" }
                    val paintDamageCount = allDetectedIssues.count { it.damageType == "Paint Damage" }
                    val algaeDetectedCount = allDetectedIssues.count { it.damageType == "Algae" }

                    var anyLine = false

                    // ✅ UPDATED: Display new damage type names
                    if (spallingCount > 0) {
                        DetailsSummaryRow("Spalling", "$spallingCount location(s) - High Risk")
                        anyLine = true
                    }
                    if (majorCrackCount > 0) {
                        DetailsSummaryRow("Major Crack", "$majorCrackCount location(s) - High Risk")
                        anyLine = true
                    }
                    if (minorCrackCount > 0) {
                        DetailsSummaryRow("Minor Crack", "$minorCrackCount location(s) - Low Risk")
                        anyLine = true
                    }
                    if (paintDamageCount > 0) {
                        DetailsSummaryRow("Paint Damage", "$paintDamageCount location(s) - Low Risk")
                        anyLine = true
                    }
                    if (algaeDetectedCount > 0) {
                        DetailsSummaryRow("Algae/Moss", "$algaeDetectedCount location(s) - Moderate Risk")
                        anyLine = true
                    }

                    if (!anyLine) {
                        DetailsSummaryRow("Overall", "No visible damage detected")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            DetailsExpandableSection(
                title = "Recommendations",
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6366F1), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("$issuesCount Issues", fontSize = 12.sp, color = Color.White)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Confidence legend (unchanged)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Confidence Level Legend:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("High", fontSize = 12.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(Color(0xFFF57C00), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Moderate", fontSize = 12.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(Color(0xFF2E7D32), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Low", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ✅ ALSO FIX: The Recommendations section needs to handle ALL issues properly
                    // Replace the Recommendations section content with:

                    val allIssues = imageAssessments.flatMap { it.detectedIssues }

                    Log.d("AssessmentDetails", "Total issues across all images: ${allIssues.size}")

                    if (allIssues.isEmpty()) {
                        // ✅ MODIFIED: Clean card for no issues detected
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "No Significant Issues",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "GOOD",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF388E3C)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "This structure appears to be in good condition.",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        // ✅ Group by damage type AND level to show each unique issue type once
                        val grouped = allIssues.groupBy { "${it.damageType}-${it.damageLevel}" }

                        grouped.forEach { (key, issuesList) ->
                            Log.d("AssessmentDetails", "Group: $key has ${issuesList.size} occurrences")

                            val first = issuesList.first()
                            val avgConf = issuesList.map { it.confidence }.average().toFloat()
                            val rec = getRecommendation(first.damageType, first.damageLevel)

                            DetailsRecommendationCard(
                                rec = rec,
                                confidence = avgConf,
                                damageLevel = first.damageLevel,
                                locationCount = issuesList.size  // ✅ ADD: Pass the location count
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            DetailsExpandableSection(
                title = "AI Analysis Results",
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6366F1), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${imageAssessments.size} Images", fontSize = 12.sp, color = Color.White)
                    }
                }
            ) {
                if (imageAssessments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        imageAssessments.forEachIndexed { index, assessment ->
                            DetailsAnalyzedImageCard(
                                imageNumber = index + 1,
                                assessment = assessment,
                                onImageClick = {
                                    selectedImageUrl = assessment.imageUrl
                                    showImageViewer = true
                                }
                            )
                            if (index < imageAssessments.size - 1) {
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                            }
                        }
                    }
                } else if (isLoadingImages) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF0288D1)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Spacer(Modifier.height(32.dp))  // ← ADD THIS LINE HERE!

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Download", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        val intent = Intent(context, HistoryActivity::class.java)
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                ) {
                    Text("Back to History", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImageViewer && selectedImageUrl != null) {
        DetailsFullScreenImageViewer(
            imageUrl = selectedImageUrl!!,
            onDismiss = {
                showImageViewer = false
                selectedImageUrl = null
            }
        )
    }

    if (showDownloadDialog) {
        DetailsDownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onDownload = {
                coroutineScope.launch {
                    try {
                        val imageUrls = imageAssessments.map { it.imageUrl }
                        val pdfData = PdfAssessmentData(
                            assessmentName = title,
                            date = date,
                            overallRisk = riskLevel,
                            totalIssues = issuesCount,
                            crackHighCount = crackHigh,
                            crackModerateCount = crackModerate,
                            crackLowCount = crackLow,
                            paintCount = paintCount,  // ✅ FIXED: Single merged count
                            algaeCount = algaeCount,  // ✅ FIXED: Single merged count
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
                            Toast.makeText(context, "PDF saved to Downloads folder!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                        }
                        showDownloadDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        showDownloadDialog = false
                    }
                }
            }
        )
    }
}

// RENAMED Helper Composables (Details prefix to avoid conflicts)
@Composable
fun DetailsSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Text(value, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun DetailsRecommendationCard(
    rec: DamageRecommendation,
    confidence: Float,
    damageLevel: String,
    locationCount: Int = 1
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    rec.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(rec.severityBgColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        rec.severity,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = rec.severityColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(rec.description, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)

            Spacer(Modifier.height(16.dp))

            // AI Confidence Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI Confidence", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = confidence,
                        modifier = Modifier.width(100.dp).height(6.dp),
                        color = detailsGetConfidenceColor(damageLevel),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.widthIn(min = 40.dp)
                    )
                }
            }

            // ✅ IMPROVED: Add divider and better spacing before actions
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            // ✅ IMPROVED: Better formatted actions section
            Text(
                "Recommended Actions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Spacer(Modifier.height(12.dp))

            // ✅ IMPROVED: Each action in a card with better spacing
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rec.actions.forEachIndexed { index, action ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF9FAFB)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // ✅ IMPROVED: Numbered circles instead of just checks
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        Color(0xFF10B981),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                action,
                                fontSize = 13.sp,
                                color = Color(0xFF374151),
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ✅ Location counter (only show if > 1)
            if (locationCount > 1) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Detected Locations",
                            fontSize = 13.sp,
                            color = Color(0xFF374151),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEEF2FF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "$locationCount images",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6366F1)
                        )
                    }
                }
            }
        }
    }
}

fun detailsGetConfidenceColor(damageLevel: String): Color {
    return when (damageLevel) {
        "High" -> Color(0xFFD32F2F)
        "Moderate" -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }
}

@Composable
fun DetailsAnalyzedImageCard(imageNumber: Int, assessment: DetailImageAssessment, onImageClick: () -> Unit) {
    // ✅ MODIFIED: Check for Plain surfaces first, then determine risk
    val isPlainSurface = assessment.plainConf > 0.30f

    val riskColor = when {
        isPlainSurface -> Color(0xFFE8F5E9)
        assessment.imageRisk == "High" -> Color(0xFFFFEBEE)
        assessment.imageRisk == "Moderate" -> Color(0xFFFFF3E0)
        //assessment.detectedIssues.any { it.damageType == "Algae" } -> Color(0xFFFFF3E0)  // Keep yellow for Algae
        assessment.imageRisk == "Low" -> Color(0xFFE8F5E9)  // Paint Damage will be green
        assessment.imageRisk == "None" -> Color(0xFFE8F5E9)
        else -> Color(0xFFF5F5F5)
    }

    val badgeColor = when {
        isPlainSurface -> Color(0xFF2E7D32)
        assessment.imageRisk == "High" -> Color(0xFFD32F2F)
        assessment.imageRisk == "Moderate" -> Color(0xFFF57C00)
        // ✅ UPDATED: Check for new damage type names
        //assessment.detectedIssues.any { it.damageType == "Paint Damage" || it.damageType == "Algae" } -> Color(0xFFF57C00)
        assessment.imageRisk == "Low" -> Color(0xFF388E3C)
        assessment.imageRisk == "None" -> Color(0xFF2E7D32)
        else -> Color.Gray
    }

    val badgeText = if (isPlainSurface) "PLAIN" else assessment.imageRisk.uppercase()  // ✅ NEW

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with image and risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image thumbnail
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .clickable { onImageClick() }
                ) {
                    if (assessment.imageUrl.isNotEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(assessment.imageUrl),
                            contentDescription = "Image $imageNumber",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = "No image",
                                tint = Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Title and risk badge
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Image $imageNumber",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Box(
                            modifier = Modifier
                                .background(riskColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                badgeText,  // ✅ MODIFIED: Use badgeText instead
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    // ✅ MODIFIED: Show different message for Plain surfaces
                    Text(
                        if (isPlainSurface) "Clean surface detected" else "${assessment.detectedIssues.size} issue(s) detected",
                        fontSize = 13.sp,
                        color = if (isPlainSurface) Color(0xFF2E7D32) else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ✅ MODIFIED: Display multiple issues OR plain surface info
            when {
                isPlainSurface -> {
                    // ✅ NEW: Show plain surface information
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Clean",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Clean surface - No damage detected",
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // ✅ Show plain confidence bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Plain Surface",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { assessment.plainConf },
                                modifier = Modifier.weight(1f).height(8.dp),
                                color = Color(0xFF2E7D32),  // Green for plain
                                trackColor = Color(0xFFE0E0E0)
                            )
                            Text(
                                "${(assessment.plainConf * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
                assessment.detectedIssues.isEmpty() -> {
                    Text(
                        "No clear detection - Review image quality.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                else -> {
                    // ✅ Show damage issues (existing logic)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        assessment.detectedIssues.sortedByDescending { it.confidence }.forEach { issue ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${issue.damageType}",  // ✅ Will show "Spalling", "Major Crack", "Minor Crack", "Paint Damage", "Algae"
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { issue.confidence },
                                        modifier = Modifier.weight(1f).height(8.dp),
                                        color = detailsGetConfidenceColor(issue.damageLevel),
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                    Text(
                                        "${(issue.confidence * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsFullScreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            IconButton(
                onClick = onDismiss,
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
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "Full screen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsExpandableSection(title: String, trailingContent: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "rotation"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trailingContent != null) {
                        trailingContent()
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .rotate(rotation)
                            .size(24.dp)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun DetailsDownloadDialog(onDismiss: () -> Unit, onDownload: () -> Unit) {
    var isGenerating by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Download Assessment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF0288D1)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Generating PDF...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                            .clickable {
                                isGenerating = true
                                onDownload()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFFF5252), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Assessment Report PDF",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            Text(
                                "Download detailed results",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Text("Cancel", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            fontSize = 14.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview
@Composable
fun AssessmentDetailsPreview() {
    MaterialTheme {
        AssessmentDetailsScreen(
            assessmentId = "preview",
            title = "Home Assessment",
            date = "October 27, 2025",
            riskLevel = "High Risk",
            issuesCount = 2,
            crackHigh = 1,
            crackModerate = 1,
            crackLow = 0,
            paintCount = 0,    // ✅ Single count
            algaeCount = 0,    // ✅ Single count
            buildingType = "Residential",
            constructionYear = "2013",
            renovationYear = "2014",
            floors = "2",
            material = "Reinforced Concrete",
            foundation = "Basement",
            environment = "Earthquake-prone area",
            previousIssues = listOf("Wall cracks"),
            occupancy = "Low",
            environmentalRisks = listOf("Earthquake-prone area"),
            notes = "Additional notes here.",
            getRecommendation = { _, _ ->
                DamageRecommendation("", "", "", emptyList(), Color.Gray, Color.LightGray)
            },
            onEditBuildingInfo = {}
        )
    }
}