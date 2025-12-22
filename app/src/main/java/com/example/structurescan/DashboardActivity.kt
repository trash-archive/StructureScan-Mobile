package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class AssessmentItem(
    val id: String,
    val assessmentName: String,
    val date: String,
    val overallRisk: String,
    val totalIssues: Int = 0,
    val crackHighCount: Int = 0,
    val crackModerateCount: Int = 0,
    val crackLowCount: Int = 0,
    val paintHighCount: Int = 0,
    val paintModerateCount: Int = 0,
    val paintLowCount: Int = 0,
    val algaeHighCount: Int = 0,
    val algaeModerateCount: Int = 0,
    val algaeLowCount: Int = 0,
    val timestamp: Long = 0,
    val buildingType: String = "",
    val constructionYear: String = "",
    val renovationYear: String = "",
    val floors: String = "",
    val material: String = "",
    val foundation: String = "",
    val environment: String = "",
    val previousIssues: List<String> = emptyList(),
    val occupancy: String = "",
    val environmentalRisks: List<String> = emptyList(),
    val notes: String = ""
)

class DashboardActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setContent {
            MaterialTheme {
                var currentRoute by remember { mutableStateOf("home") }
                DashboardScreen(
                    currentRoute = currentRoute,
                    onRouteChange = { newRoute -> currentRoute = newRoute },
                    onScanClick = {
                        val intent = Intent(this, ScanActivity::class.java)
                        startActivity(intent)
                    },
                    onQuickScanClick = {
                        startActivity(Intent(this, QuickScanActivity::class.java))
                    },
                    onQuickTiltClick = {
                        val intent = Intent(this, QuickTiltAnalysisActivity::class.java)
                        startActivity(intent)
                    },
                    firestore = firestore,
                    firebaseAuth = firebaseAuth
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    currentRoute: String,
    onRouteChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onQuickScanClick: () -> Unit,
    onQuickTiltClick: () -> Unit,
    firestore: FirebaseFirestore? = null,
    firebaseAuth: FirebaseAuth? = null
) {
    var assessments by remember { mutableStateOf<List<AssessmentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Quick Tools Help Dialog State
    var showQuickToolsHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("assessments")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("DashboardActivity", "Error fetching assessments", error)
                        isLoading = false
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val fetchedAssessments = snapshot.documents.mapNotNull { doc ->
                            try {
                                val previousIssuesList = doc.get("previousIssues") as? List<*>
                                val previousIssuesStrings = previousIssuesList?.mapNotNull { it as? String } ?: emptyList()

                                val environmentalRisksList = doc.get("environmentalRisks") as? List<*>
                                val environmentalRisksStrings = environmentalRisksList?.mapNotNull { it as? String } ?: emptyList()

                                AssessmentItem(
                                    id = doc.id,
                                    assessmentName = doc.getString("assessmentName") ?: "Unnamed Assessment",
                                    date = doc.getString("date") ?: "",
                                    overallRisk = doc.getString("overallRisk") ?: "Unknown",
                                    totalIssues = doc.getLong("totalIssues")?.toInt() ?: 0,
                                    crackHighCount = doc.getLong("crackHighCount")?.toInt() ?: 0,
                                    crackModerateCount = doc.getLong("crackModerateCount")?.toInt() ?: 0,
                                    crackLowCount = doc.getLong("crackLowCount")?.toInt() ?: 0,
                                    paintHighCount = doc.getLong("paintHighCount")?.toInt() ?: 0,
                                    paintModerateCount = doc.getLong("paintModerateCount")?.toInt() ?: 0,
                                    paintLowCount = doc.getLong("paintLowCount")?.toInt() ?: 0,
                                    algaeHighCount = doc.getLong("algaeHighCount")?.toInt() ?: 0,
                                    algaeModerateCount = doc.getLong("algaeModerateCount")?.toInt() ?: 0,
                                    algaeLowCount = doc.getLong("algaeLowCount")?.toInt() ?: 0,
                                    timestamp = doc.getLong("timestamp") ?: 0,
                                    buildingType = doc.getString("buildingType") ?: "",
                                    constructionYear = doc.getString("constructionYear") ?: "",
                                    renovationYear = doc.getString("renovationYear") ?: "",
                                    floors = doc.getString("floors") ?: "",
                                    material = doc.getString("material") ?: "",
                                    foundation = doc.getString("foundation") ?: "",
                                    environment = doc.getString("environment") ?: "",
                                    previousIssues = previousIssuesStrings,
                                    occupancy = doc.getString("occupancy") ?: "",
                                    environmentalRisks = environmentalRisksStrings,
                                    notes = doc.getString("notes") ?: ""
                                )
                            } catch (e: Exception) {
                                Log.e("DashboardActivity", "Error parsing document ${e.message}")
                                null
                            }
                        }
                        assessments = fetchedAssessments
                        isLoading = false
                    } else {
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBarDashboard(
                currentRoute = currentRoute,
                onRouteChange = onRouteChange
            )
        }
    ) { padding ->
        if (currentRoute == "home") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // App Title
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "StructureScan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0288D1)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // MAIN HERO CARD - Start New Assessment
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1C3D6C),
                                            Color(0xFF2d5a8f)
                                        )
                                    )
                                )
                                .padding(32.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Start New",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Take photos of your building to get an instant structural analysis",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onScanClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    elevation = ButtonDefaults.buttonElevation(4.dp)
                                ) {
                                    Text(
                                        "Scan Now",
                                        color = Color(0xFF1C3D6C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

// QUICK TOOLS SECTION - INFO ICON NEXT TO TITLE WITH PROPER SPACING
                item {
                    // Quick Tools Header - Title + Info Icon side-by-side
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Tools Title
                        Text(
                            text = "Quick Tools",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            modifier = Modifier.weight(1f)  // Takes remaining space
                        )

                        // Info Icon RIGHT NEXT TO title with proper spacing
                        Spacer(modifier = Modifier.width(8.dp))  // Proper spacing between title and icon

                        IconButton(
                            onClick = { showQuickToolsHelpDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Quick Tools Help",
                                tint = Color(0xFF666666),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Quick Actions Grid (unchanged)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Quick Scan",
                            description = "Instant analysis with 1 or more photos",
                            badgeText = "FAST",
                            accentColor = Color(0xFF4CAF50),
                            lightColor = Color(0xFFE8F5E9),
                            icon = Icons.Filled.PhotoCamera,
                            onClick = onQuickScanClick
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Tilt Analysis",
                            description = "Detect structural tilt with gyroscope",
                            badgeText = "SENSOR",
                            accentColor = Color(0xFF0288D1),
                            lightColor = Color(0xFFE1F5FE),
                            icon = Icons.Filled.ScreenRotation,
                            onClick = onQuickTiltClick
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }


                // Recent Assessments Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Assessments",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )
                        if (assessments.isNotEmpty()) {
                            Text(
                                text = "${assessments.size} total",
                                fontSize = 12.sp,
                                color = Color(0xFF999999)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Loading, Empty State, or Assessments List
                when {
                    isLoading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0288D1),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    assessments.isEmpty() -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No assessments yet",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF999999)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Start your first assessment to see it here",
                                        fontSize = 14.sp,
                                        color = Color(0xFFBBBBBB)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        items(assessments) { assessment ->
                            AssessmentCard(assessment = assessment)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // QUICK TOOLS HELP DIALOG
    if (showQuickToolsHelpDialog) {
        AlertDialog(
            onDismissRequest = { showQuickToolsHelpDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Quick Tools",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Fast screening tools for on-site inspections. All work completely OFFLINE."
                    )
                    Text("Capabilities:", fontWeight = FontWeight.SemiBold)
                    Text("• Quick Scan: AI damage detection from 1 or more photos")
                    Text("• Quick Tilt: Single-photo structural lean analysis")
                    Text("Limitations:", fontWeight = FontWeight.SemiBold)
                    Text("• Results NOT saved to history or cloud",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280))
                    Text("• For screening only - consult engineer for issues",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280))
                    Text("• No photo storage or sharing",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280))
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickToolsHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}


@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    badgeText: String,
    accentColor: Color,
    lightColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(20.dp, 20.dp, 16.dp, 20.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(lightColor, lightColor.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF666666),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = lightColor
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun AssessmentCard(assessment: AssessmentItem) {
    val context = LocalContext.current
    val riskColor = when (assessment.overallRisk) {
        "High Risk", "UNSAFE" -> Color(0xFFD32F2F)
        "Moderate Risk", "RESTRICTED" -> Color(0xFFF57C00)
        "Low Risk", "INSPECTED" -> Color(0xFF388E3C)
        "GOOD" -> Color(0xFF2E7D32)
        else -> Color.Gray
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable {
                val intent = Intent(context, AssessmentDetailsActivity::class.java).apply {
                    putExtra("ASSESSMENT_ID", assessment.id)
                    putExtra("ASSESSMENT_TITLE", assessment.assessmentName)
                    putExtra("ASSESSMENT_DATE", assessment.date)
                    putExtra("RISK_LEVEL", assessment.overallRisk)
                    putExtra("ISSUES_COUNT", assessment.totalIssues)
                    putExtra("CRACK_HIGH", assessment.crackHighCount)
                    putExtra("CRACK_MODERATE", assessment.crackModerateCount)
                    putExtra("CRACK_LOW", assessment.crackLowCount)
                    putExtra("PAINT_HIGH", assessment.paintHighCount)
                    putExtra("PAINT_MODERATE", assessment.paintModerateCount)
                    putExtra("PAINT_LOW", assessment.paintLowCount)
                    putExtra("ALGAE_HIGH", assessment.algaeHighCount)
                    putExtra("ALGAE_MODERATE", assessment.algaeModerateCount)
                    putExtra("ALGAE_LOW", assessment.algaeLowCount)
                    putExtra("BUILDING_TYPE", assessment.buildingType)
                    putExtra("CONSTRUCTION_YEAR", assessment.constructionYear)
                    putExtra("RENOVATION_YEAR", assessment.renovationYear)
                    putExtra("FLOORS", assessment.floors)
                    putExtra("MATERIAL", assessment.material)
                    putExtra("FOUNDATION", assessment.foundation)
                    putExtra("ENVIRONMENT", assessment.environment)
                    putStringArrayListExtra("PREVIOUS_ISSUES", ArrayList(assessment.previousIssues))
                    putExtra("OCCUPANCY", assessment.occupancy)
                    putStringArrayListExtra("ENVIRONMENTAL_RISKS", ArrayList(assessment.environmentalRisks))
                    putExtra("NOTES", assessment.notes)
                }
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFE8F5E8), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.homeassesment),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assessment.assessmentName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = assessment.date,
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Issues Detected: ${assessment.totalIssues}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = riskColor
                ) {
                    Text(
                        text = assessment.overallRisk,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBarDashboard(
    currentRoute: String,
    onRouteChange: (String) -> Unit
) {
    val context = LocalContext.current
    NavigationBar(
        containerColor = Color.White,
        modifier = Modifier.height(80.dp)
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { onRouteChange("home") },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "Home",
                    fontSize = 12.sp,
                    color = if (currentRoute == "home") Color(0xFF0288D1) else Color.Gray
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick = {
                val intent = Intent(context, HistoryActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History",
                    tint = if (currentRoute == "history") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "History",
                    fontSize = 12.sp,
                    color = if (currentRoute == "history") Color(0xFF0288D1) else Color.Gray
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = {
                val intent = Intent(context, ProfileActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "profile") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "Profile",
                    fontSize = 12.sp,
                    color = if (currentRoute == "profile") Color(0xFF0288D1) else Color.Gray
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    MaterialTheme {
        DashboardScreen(
            currentRoute = "home",
            onRouteChange = { },
            onScanClick = { },
            onQuickScanClick = { },
            onQuickTiltClick = { },
            firestore = null,
            firebaseAuth = null
        )
    }
}
