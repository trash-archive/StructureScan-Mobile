package com.example.structurescan
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BuildingInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve data from intent
        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: ""
        val buildingAreas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, BuildingArea::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS)
        } ?: arrayListOf()

        setContent {
            MaterialTheme {
                BuildingInfoScreen(
                    onBack = {
                        finish()
                    },
                    onSubmit = { buildingInfo ->
                        // Pass all data to AssessmentResultsActivity
                        val intent = Intent(this, AssessmentResultsActivity::class.java).apply {
                            putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                            putParcelableArrayListExtra(
                                IntentKeys.BUILDING_AREAS,
                                ArrayList(buildingAreas)
                            )

                            // Building info
                            putExtra(IntentKeys.BUILDING_TYPE, buildingInfo.buildingType)
                            putExtra(IntentKeys.CONSTRUCTION_YEAR, buildingInfo.constructionYear)
                            putExtra(IntentKeys.RENOVATION_YEAR, buildingInfo.renovationYear)
                            putExtra(IntentKeys.FLOORS, buildingInfo.floors)
                            putExtra(IntentKeys.MATERIAL, buildingInfo.material)
                            putExtra(IntentKeys.FOUNDATION, buildingInfo.foundation)
                            putExtra(IntentKeys.ENVIRONMENT, buildingInfo.environment)
                            putStringArrayListExtra(IntentKeys.PREVIOUS_ISSUES, ArrayList(buildingInfo.previousIssues))
                            putExtra(IntentKeys.OCCUPANCY, buildingInfo.occupancy)
                            putStringArrayListExtra(IntentKeys.ENVIRONMENTAL_RISKS, ArrayList(buildingInfo.environmentalRisks))
                            putExtra(IntentKeys.NOTES, buildingInfo.notes)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

// Data class to hold building information
data class BuildingInfo(
    val buildingType: String,
    val constructionYear: String,
    val renovationYear: String,
    val floors: String,
    val material: String,
    val foundation: String,
    val environment: String,
    val previousIssues: List<String>,
    val occupancy: String,
    val environmentalRisks: List<String>,
    val notes: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingInfoScreen(onBack: () -> Unit, onSubmit: (BuildingInfo) -> Unit) {
    val context = LocalContext.current

    // --- State variables ---
    var buildingType by remember { mutableStateOf("") }
    var constructionYear by remember { mutableStateOf("") }
    var renovationYear by remember { mutableStateOf("") }
    var floors by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var foundation by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Issues checkboxes
    val selectedIssues = remember { mutableStateListOf<String>() }

    // Occupancy (radio) - NO DEFAULT
    val occupancyOptions = listOf("Low", "Average", "High")
    var selectedOccupancy by remember { mutableStateOf("") }

    // Environmental risks checkboxes
    val selectedRisks = remember { mutableStateListOf<String>() }

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
                    text = "Building Information",
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
                            "Optional Building Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4A86E8)
                        )
                        Text(
                            "This information helps with documentation",
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

            // Continue Button with VALIDATION
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

                    val buildingInfo = BuildingInfo(
                        buildingType = buildingType,
                        constructionYear = constructionYear,
                        renovationYear = renovationYear,
                        floors = floors,
                        material = material,
                        foundation = foundation,
                        environment = environment,
                        previousIssues = selectedIssues.toList(),
                        occupancy = selectedOccupancy,
                        environmentalRisks = selectedRisks.toList(),
                        notes = notes
                    )
                    onSubmit(buildingInfo)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    "Continue",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "All fields are optional • You can skip this",
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BuildingInfoPreview() {
    MaterialTheme {
        BuildingInfoScreen(onBack = {}, onSubmit = {})
    }
}