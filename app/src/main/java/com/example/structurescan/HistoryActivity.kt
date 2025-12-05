package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class SavedAssessment(
    val id: String = "",
    val assessmentName: String = "",
    val date: String = "",
    val overallRisk: String = "",
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
    val timestamp: Long = 0L,
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

class HistoryActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setContent {
            MaterialTheme {
                HistoryScreen(firestore = firestore, firebaseAuth = firebaseAuth)
            }
        }
    }
}

@Composable
fun HistoryScreen(
    firestore: FirebaseFirestore? = null,
    firebaseAuth: FirebaseAuth? = null
) {
    val context = LocalContext.current

    // ✅ CHANGED: Sort options instead of building types
    val sortOptions = listOf("Newest First", "Oldest First")
    var expanded by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf("Newest First") }

    // ✅ NEW: Search state
    var searchQuery by remember { mutableStateOf("") }

    var assessments by remember { mutableStateOf<List<SavedAssessment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedAssessments = remember { mutableStateListOf<String>() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Exit selection mode OR go to Dashboard
    BackHandler(enabled = true) {
        if (isSelectionMode) {
            // Exit selection mode
            isSelectionMode = false
            selectedAssessments.clear()
        } else {
            // Navigate to Dashboard
            val intent = Intent(context, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
    }

    LaunchedEffect(Unit) {
        if (firebaseAuth == null || firestore == null) {
            // Preview mode - don't try to load data
            isLoading = false
            return@LaunchedEffect
        }

        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            errorMessage = "Please log in to view history"
            isLoading = false
            return@LaunchedEffect
        }

        firestore.collection("users")
            .document(currentUser.uid)
            .collection("assessments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val loadedAssessments = documents.mapNotNull { doc ->
                    try {
                        val previousIssuesList = doc.get("previousIssues") as? List<*>
                        val previousIssuesStrings = previousIssuesList?.mapNotNull { it as? String } ?: emptyList()

                        val environmentalRisksList = doc.get("environmentalRisks") as? List<*>
                        val environmentalRisksStrings = environmentalRisksList?.mapNotNull { it as? String } ?: emptyList()

                        SavedAssessment(
                            id = doc.id,
                            assessmentName = doc.getString("assessmentName") ?: "Unnamed",
                            date = doc.getString("date") ?: "",
                            overallRisk = doc.getString("overallRisk") ?: "Low Risk",
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
                            timestamp = doc.getLong("timestamp") ?: 0L,
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
                        Log.e("HistoryActivity", "Error parsing document: ${e.message}")
                        null
                    }
                }
                assessments = loadedAssessments
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorMessage = "Failed to load history: ${e.message}"
                isLoading = false
                Log.e("HistoryActivity", "Error loading assessments", e)
            }
    }

    // ✅ Filter and sort assessments
    val filteredAndSortedAssessments = remember(assessments, searchQuery, selectedSort) {
        // First filter by search query
        val filtered = if (searchQuery.isBlank()) {
            assessments
        } else {
            assessments.filter { it.assessmentName.contains(searchQuery, ignoreCase = true) }
        }

        // Then sort by date
        when (selectedSort) {
            "Newest First" -> filtered.sortedByDescending { it.timestamp }
            "Oldest First" -> filtered.sortedBy { it.timestamp }
            else -> filtered
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Assessments",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text("Are you sure you want to delete ${selectedAssessments.size} assessment(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentUser = firebaseAuth?.currentUser
                        if (currentUser != null && firestore != null) {
                            selectedAssessments.forEach { assessmentId ->
                                firestore.collection("users")
                                    .document(currentUser.uid)
                                    .collection("assessments")
                                    .document(assessmentId)
                                    .delete()
                                    .addOnSuccessListener {
                                        Log.d("HistoryActivity", "Assessment deleted: $assessmentId")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("HistoryActivity", "Error deleting assessment", e)
                                    }
                            }

                            assessments = assessments.filter { !selectedAssessments.contains(it.id) }
                            selectedAssessments.clear()
                            isSelectionMode = false
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFE53935)
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (!isSelectionMode) {
                BottomNavigationBarHistory(currentScreen = "history")
            }
        },
        topBar = {
            if (isSelectionMode) {
                Surface(
                    color = Color(0xFF0288D1),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedAssessments.clear()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection mode",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "${selectedAssessments.size} selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selectedAssessments.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (selectedAssessments.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(innerPadding)
        ) {
            if (!isSelectionMode) {
                // Top Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Back Button (Left)
                    IconButton(
                        onClick = {
                            val intent = Intent(context, DashboardActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Centered Title
                    Text(
                        text = "Assessment History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0288D1),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // ✅ NEW: Search Field and Sort Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search assessments...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Gray
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0288D1),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sort dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sort by date",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Transparent)
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSort,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0288D1),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dropdown",
                                    tint = Color(0xFF0288D1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                fontSize = 14.sp,
                                                color = Color.Black
                                            )
                                        },
                                        onClick = {
                                            selectedSort = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(
                    color = Color(0xFFE0E0E0),
                    thickness = 0.5.dp
                )
            }

            // Main content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA))
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF0288D1))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Loading assessments...",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    errorMessage ?: "Unknown error",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    filteredAndSortedAssessments.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = "No history",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (searchQuery.isBlank())
                                        "No assessments yet"
                                    else
                                        "No results for \"$searchQuery\"",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                Text(
                                    if (searchQuery.isBlank())
                                        "Start scanning to build your history"
                                    else
                                        "Try a different search term",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8F9FA))
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            items(filteredAndSortedAssessments) { assessment ->
                                HistoryCard(
                                    title = assessment.assessmentName,
                                    date = assessment.date,
                                    riskLevel = assessment.overallRisk,
                                    issuesCount = assessment.totalIssues,
                                    riskColor = when (assessment.overallRisk) {
                                        "High Risk" -> Color(0xFFE53935)
                                        "Moderate Risk" -> Color(0xFFFFA726)
                                        else -> Color(0xFF4CAF50)
                                    },
                                    assessment = assessment,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedAssessments.contains(assessment.id),
                                    onLongPress = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedAssessments.add(assessment.id)
                                        }
                                    },
                                    onSelectionToggle = {
                                        if (selectedAssessments.contains(assessment.id)) {
                                            selectedAssessments.remove(assessment.id)
                                            if (selectedAssessments.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            selectedAssessments.add(assessment.id)
                                        }
                                    }
                                )
                            }

                            item { Spacer(modifier = Modifier.height(20.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    title: String,
    date: String,
    riskLevel: String,
    issuesCount: Int,
    riskColor: Color,
    assessment: SavedAssessment,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle()
                    } else {
                        val intent = Intent(context, AssessmentDetailsActivity::class.java).apply {
                            putExtra("ASSESSMENT_ID", assessment.id)
                            putExtra("ASSESSMENT_TITLE", title)
                            putExtra("ASSESSMENT_DATE", date)
                            putExtra("RISK_LEVEL", riskLevel)
                            putExtra("ISSUES_COUNT", issuesCount)
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
                },
                onLongClick = {
                    onLongPress()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF0288D1),
                        uncheckedColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = date,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(riskColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = riskLevel,
                        fontSize = 14.sp,
                        color = riskColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "$issuesCount issues detected",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            if (!isSelectionMode) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBarHistory(currentScreen: String) {
    val context = LocalContext.current
    NavigationBar(
        containerColor = Color.White,
        modifier = Modifier.height(64.dp)
    ) {
        NavigationBarItem(
            selected = currentScreen == "home",
            onClick = {
                val intent = Intent(context, DashboardActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    "Home",
                    fontSize = 12.sp
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
            selected = currentScreen == "history",
            onClick = { /* Already on this screen */ },
            icon = {
                Icon(
                    Icons.Filled.History,
                    contentDescription = "History",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    "History",
                    fontSize = 12.sp
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
            selected = currentScreen == "profile",
            onClick = {
                val intent = Intent(context, ProfileActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    "Profile",
                    fontSize = 12.sp
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
fun HistoryPreview() {
    MaterialTheme {
        // ✅ Pass null for Firebase instances in preview mode
        HistoryScreen(firestore = null, firebaseAuth = null)
    }
}