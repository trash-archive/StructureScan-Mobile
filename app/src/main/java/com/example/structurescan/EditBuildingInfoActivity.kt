package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditBuildingInfoActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // ✅ Receive building data from intent
        val assessmentName = intent.getStringExtra("assessmentName") ?: "Home Assessment"
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
                EditBuildingInfoScreen(
                    initialAssessmentName = assessmentName,
                    initialBuildingType = buildingType,
                    initialConstructionYear = constructionYear,
                    initialRenovationYear = renovationYear,
                    initialFloors = floors,
                    initialMaterial = material,
                    initialFoundation = foundation,
                    initialEnvironment = environment,
                    initialPreviousIssues = previousIssues,
                    initialOccupancy = occupancy,
                    initialEnvironmentalRisks = environmentalRisks,
                    initialNotes = notes,
                    onBack = { finish() },
                    onUpdateAnalysis = { updatedData ->
                        updateBuildingInfoInFirebase(updatedData, assessmentName)
                    },
                    onDeleteAssessment = {
                        deleteAssessmentFromFirebase(assessmentName)
                    }
                )
            }
        }
    }

    // ✅ Update building info in Firebase
    private fun updateBuildingInfoInFirebase(updatedData: Map<String, Any>, originalAssessmentName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Query to find the assessment document by original name
        firestore.collection("users")
            .document(userId)
            .collection("assessments")
            .whereEqualTo("assessmentName", originalAssessmentName)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e("EditBuildingInfo", "Assessment not found: $originalAssessmentName")
                    Toast.makeText(this, "Assessment not found in database", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Update the first matching document
                val documentId = documents.documents[0].id
                Log.d("EditBuildingInfo", "Updating document: $documentId with data: $updatedData")

                firestore.collection("users")
                    .document(userId)
                    .collection("assessments")
                    .document(documentId)
                    .update(updatedData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Building information updated successfully!", Toast.LENGTH_SHORT).show()

                        // ✅ Return to AssessmentResultsActivity with updated data
                        val resultIntent = Intent().apply {
                            putExtra("UPDATED", true)
                            putExtra("assessmentName", updatedData["assessmentName"] as? String ?: originalAssessmentName)
                            putExtra(IntentKeys.BUILDING_TYPE, updatedData["buildingType"] as? String ?: "")
                            putExtra(IntentKeys.CONSTRUCTION_YEAR, updatedData["constructionYear"] as? String ?: "")
                            putExtra(IntentKeys.RENOVATION_YEAR, updatedData["renovationYear"] as? String ?: "")
                            putExtra(IntentKeys.FLOORS, updatedData["floors"] as? String ?: "")
                            putExtra(IntentKeys.MATERIAL, updatedData["material"] as? String ?: "")
                            putExtra(IntentKeys.FOUNDATION, updatedData["foundation"] as? String ?: "")
                            putExtra(IntentKeys.ENVIRONMENT, updatedData["environment"] as? String ?: "")
                            putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, ArrayList(updatedData["previousIssues"] as? List<String> ?: emptyList()))
                            putExtra(IntentKeys.OCCUPANCY, updatedData["occupancy"] as? String ?: "")
                            putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, ArrayList(updatedData["environmentalRisks"] as? List<String> ?: emptyList()))
                            putExtra(IntentKeys.NOTES, updatedData["notes"] as? String ?: "")
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditBuildingInfo", "Update failed", e)
                        Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("EditBuildingInfo", "Query failed", e)
                Toast.makeText(this, "Error finding assessment: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ✅ Delete assessment from Firebase
    private fun deleteAssessmentFromFirebase(assessmentName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users")
            .document(userId)
            .collection("assessments")
            .whereEqualTo("assessmentName", assessmentName)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e("EditBuildingInfo", "Assessment not found for deletion: $assessmentName")
                    Toast.makeText(this, "Assessment not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val documentId = documents.documents[0].id
                Log.d("EditBuildingInfo", "Deleting document: $documentId")

                firestore.collection("users")
                    .document(userId)
                    .collection("assessments")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "✓ Assessment deleted successfully", Toast.LENGTH_SHORT).show()

                        // Navigate to Dashboard
                        val intent = Intent(this, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("EditBuildingInfo", "Delete failed", e)
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("EditBuildingInfo", "Query failed for deletion", e)
                Toast.makeText(this, "Error finding assessment: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBuildingInfoScreen(
    initialAssessmentName: String = "Home Assessment",
    initialBuildingType: String = "",
    initialConstructionYear: String = "",
    initialRenovationYear: String = "",
    initialFloors: String = "",
    initialMaterial: String = "",
    initialFoundation: String = "",
    initialEnvironment: String = "",
    initialPreviousIssues: List<String> = emptyList(),
    initialOccupancy: String = "",
    initialEnvironmentalRisks: List<String> = emptyList(),
    initialNotes: String = "",
    onBack: () -> Unit,
    onUpdateAnalysis: (Map<String, Any>) -> Unit,
    onDeleteAssessment: () -> Unit
) {
    val context = LocalContext.current

    // --- State variables - Initialize with passed data ---
    var structureTitle by remember { mutableStateOf(initialAssessmentName) }
    var buildingType by remember { mutableStateOf(initialBuildingType) }
    var constructionYear by remember { mutableStateOf(initialConstructionYear) }
    var renovationYear by remember { mutableStateOf(initialRenovationYear) }
    var floors by remember { mutableStateOf(initialFloors) }
    var material by remember { mutableStateOf(initialMaterial) }
    var foundation by remember { mutableStateOf(initialFoundation) }
    var environment by remember { mutableStateOf(initialEnvironment) }
    var notes by remember { mutableStateOf(initialNotes) }

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }

    // Issues checkboxes - pre-populate with initial data
    val selectedIssues = remember { mutableStateListOf(*initialPreviousIssues.toTypedArray()) }

    // Occupancy (radio)
    val occupancyOptions = listOf("Low", "Average", "High")
    var selectedOccupancy by remember { mutableStateOf(initialOccupancy) }

    // Environmental risks checkboxes - pre-populate with initial data
    val selectedRisks = remember { mutableStateListOf(*initialEnvironmentalRisks.toTypedArray()) }

    // --- Delete Confirmation Dialog ---
    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
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
                        text = "Delete Assessment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Are you sure you want to delete this building assessment?",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "This action cannot be undone.",
                        fontSize = 14.sp,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF6B7280)
                            )
                        ) {
                            Text(
                                "Cancel",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteAssessment()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Delete",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Clean input field composable ---
    @Composable
    fun CleanInputField(
        label: String,
        placeholder: String,
        value: String,
        onValueChange: (String) -> Unit,
        keyboardType: KeyboardType = KeyboardType.Text
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        placeholder,
                        color = Color(0xFF9CA3AF),
                        fontSize = 16.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedTextColor = Color.Black,
                    focusedTextColor = Color.Black
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
        }
    }

    // --- Clean dropdown field composable ---
    @Composable
    fun CleanDropdownField(
        label: String,
        placeholder: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = {
                        Text(
                            placeholder,
                            color = Color(0xFF9CA3AF),
                            fontSize = 16.sp
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = Color(0xFF6B7280)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedTextColor = if (selected.isEmpty()) Color(0xFF9CA3AF) else Color.Black,
                        focusedTextColor = Color.Black,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedBorderColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 16.sp) },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Main Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
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
                // Back button (left aligned)
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Centered Title
                Text(
                    text = "Edit Building Information",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFE8F2FF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF2196F3), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "i",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            "Edit Building Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4A86E8)
                        )
                        Text(
                            "Update the information about this structure",
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Building Type
            CleanDropdownField(
                label = "Building Type",
                placeholder = "e.g., Residential, Commercial",
                options = listOf("Residential", "Commercial", "Industrial", "Mixed-use"),
                selected = buildingType
            ) { buildingType = it }

            Spacer(modifier = Modifier.height(24.dp))

            // Two-column layout for Construction Year and Last Renovation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CleanInputField(
                        label = "Construction Year",
                        placeholder = "e.g., 2010",
                        value = constructionYear,
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 4) {
                                constructionYear = it
                            }
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CleanInputField(
                        label = "Last Renovation",
                        placeholder = "e.g., 2020",
                        value = renovationYear,
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 4) {
                                renovationYear = it
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Number of Floors
            CleanInputField(
                label = "Number of Floors",
                placeholder = "e.g., 2",
                value = floors,
                keyboardType = KeyboardType.Number,
                onValueChange = {
                    if (it.all { char -> char.isDigit() } && it.length <= 3) {
                        floors = it
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Structural Material
            CleanDropdownField(
                label = "Main Structural Material",
                placeholder = "e.g., Concrete, Wood, Steel",
                options = listOf(
                    "Reinforced Concrete",
                    "Wood",
                    "Steel",
                    "Composite Material",
                    "Brick",
                    "Masonry",
                    "Stone"
                ),
                selected = material
            ) { material = it }

            Spacer(modifier = Modifier.height(24.dp))

            // Foundation Type
            CleanDropdownField(
                label = "Foundation Type",
                placeholder = "e.g., Slab-on-grade",
                options = listOf(
                    "Spread Footing",
                    "Slab-on-grade",
                    "Crawl space",
                    "Basement",
                    "Pile foundation",
                    "Mat foundation"
                ),
                selected = foundation
            ) { foundation = it }

            Spacer(modifier = Modifier.height(24.dp))

            // Environment Type
            CleanDropdownField(
                label = "Environment Type",
                placeholder = "e.g., Urban, Coastal",
                options = listOf("Urban", "Suburban", "Rural", "Coastal", "Mountainous"),
                selected = environment
            ) { environment = it }

            Spacer(modifier = Modifier.height(32.dp))

            // Previous Issues or Repairs
            Text(
                "Previous Issues or Repairs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Select any issues noticed before",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Issues checkboxes
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Wall cracks",
                    "Foundation settling",
                    "Water damage",
                    "Recent repairs"
                ).forEach { issue ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = issue in selectedIssues,
                            onCheckedChange = {
                                if (it) selectedIssues.add(issue) else selectedIssues.remove(issue)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF2196F3),
                                uncheckedColor = Color(0xFF9CA3AF)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            issue,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Building Usage
            Text(
                "Building Usage",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Select occupancy level",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Occupancy radio buttons
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                occupancyOptions.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedOccupancy == level,
                            onClick = { selectedOccupancy = level },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF2196F3),
                                unselectedColor = Color(0xFF9CA3AF)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            level,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Environmental Exposure
            Text(
                "Environmental Exposure",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Select any known natural disaster risks",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Environmental risks checkboxes
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Earthquake-prone Area",
                    "High Winds",
                    "Flood Zone",
                    "Landslide Risk",
                    "Coastal Erosion"
                ).forEach { risk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = risk in selectedRisks,
                            onCheckedChange = {
                                if (it) selectedRisks.add(risk) else selectedRisks.remove(risk)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF2196F3),
                                uncheckedColor = Color(0xFF9CA3AF)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            risk,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Additional Notes
            Text(
                "Additional Notes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = {
                    Text(
                        "e.g., Recent water leaks in basement, visible foundation cracks...",
                        color = Color(0xFF9CA3AF),
                        fontSize = 16.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedTextColor = Color.Black,
                    focusedTextColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Update Button with VALIDATION
            Button(
                onClick = {
                    // Validate renovation year is not earlier than construction year
                    if (constructionYear.isNotEmpty() && renovationYear.isNotEmpty()) {
                        val constructionYearInt = constructionYear.toIntOrNull()
                        val renovationYearInt = renovationYear.toIntOrNull()

                        if (constructionYearInt != null && renovationYearInt != null) {
                            if (renovationYearInt < constructionYearInt) {
                                Toast.makeText(
                                    context,
                                    "Last renovation year cannot be earlier than construction year",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                        }
                    }

                    isUpdating = true

                    val updatedData = hashMapOf<String, Any>(
                        "assessmentName" to structureTitle,
                        "buildingType" to buildingType,
                        "constructionYear" to constructionYear,
                        "renovationYear" to renovationYear,
                        "floors" to floors,
                        "material" to material,
                        "foundation" to foundation,
                        "environment" to environment,
                        "previousIssues" to selectedIssues.toList(),
                        "occupancy" to selectedOccupancy,
                        "environmentalRisks" to selectedRisks.toList(),
                        "notes" to notes
                    )

                    onUpdateAnalysis(updatedData)
                },
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    disabledContainerColor = Color(0xFF2196F3).copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Update Information",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delete Button
            Button(
                onClick = { showDeleteDialog = true },
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    disabledContainerColor = Color(0xFFDC2626).copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    "Delete Assessment",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditBuildingInfoPreview() {
    MaterialTheme {
        EditBuildingInfoScreen(
            onBack = {},
            onUpdateAnalysis = {},
            onDeleteAssessment = {}
        )
    }
}