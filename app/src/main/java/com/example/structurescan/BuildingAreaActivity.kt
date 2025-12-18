package com.example.structurescan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.util.*

class BuildingAreaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"

        setContent {
            MaterialTheme {
                BuildingAreaScreen(
                    assessmentName = assessmentName,
                    onBackClick = {
                        finish()
                    },
                    onProceedToInfo = { areas ->
                        val intent = Intent(this, BuildingInfoActivity::class.java).apply {
                            putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                            putParcelableArrayListExtra(
                                IntentKeys.BUILDING_AREAS,
                                ArrayList(areas)
                            )
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingAreaScreen(
    assessmentName: String,
    onBackClick: () -> Unit,
    onProceedToInfo: (List<BuildingArea>) -> Unit
) {
    var buildingAreas by remember { mutableStateOf<List<BuildingArea>>(emptyList()) }
    var currentScreen by remember { mutableStateOf<AreaScreen>(AreaScreen.AreaList) }
    var selectedArea by remember { mutableStateOf<BuildingArea?>(null) }
    var showAreaDialog by remember { mutableStateOf(false) }
    var areaToEdit by remember { mutableStateOf<BuildingArea?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var areaToDelete by remember { mutableStateOf<BuildingArea?>(null) }

    Scaffold(
        topBar = {
            // Updated Top Bar matching your style
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
                            when (currentScreen) {
                                AreaScreen.AreaList -> onBackClick()
                                AreaScreen.CameraCapture -> currentScreen = AreaScreen.AreaList
                            }
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
                        text = assessmentName,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                AreaScreen.AreaList -> {
                    AreaListScreen(
                        areas = buildingAreas,
                        onAddPhotoClick = { area ->
                            selectedArea = area
                            currentScreen = AreaScreen.CameraCapture
                        },
                        onEditClick = { area ->
                            areaToEdit = area
                            showAreaDialog = true
                        },
                        onDeleteClick = { area ->
                            areaToDelete = area
                            showDeleteDialog = true
                        },
                        onCreateNewArea = {
                            areaToEdit = null
                            showAreaDialog = true
                        },
                        onProceedToInfo = { onProceedToInfo(buildingAreas) }
                    )
                }

                AreaScreen.CameraCapture -> {
                    selectedArea?.let { area ->
                        CameraCaptureScreen(
                            areaName = area.name,
                            onPhotoCaptured = { uri, tilt ->
                                val updatedArea = area.copy(
                                    photos = area.photos + uri,
                                    photoTilts = area.photoTilts + tilt
                                )
                                buildingAreas = buildingAreas.map {
                                    if (it.id == area.id) updatedArea else it
                                }
                                selectedArea = updatedArea
                                currentScreen = AreaScreen.AreaList
                            },
                            onBack = {
                                currentScreen = AreaScreen.AreaList
                            }
                        )
                    }
                }
            }
        }
    }

    // Area Dialog (Create or Edit)
    if (showAreaDialog) {
        AreaDialog(
            area = areaToEdit,
            onDismiss = {
                showAreaDialog = false
                areaToEdit = null
            },
            onSave = { area ->
                if (areaToEdit == null) {
                    // Creating new area
                    buildingAreas = buildingAreas + area
                } else {
                    // Editing existing area
                    buildingAreas = buildingAreas.map {
                        if (it.id == area.id) area else it
                    }
                }
                showAreaDialog = false
                areaToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && areaToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                areaToDelete = null
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Delete Area?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${areaToDelete!!.name}\"? " +
                            "This will remove ${areaToDelete!!.photos.size} photo(s). This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        buildingAreas = buildingAreas.filter { it.id != areaToDelete!!.id }
                        showDeleteDialog = false
                        areaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    areaToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AreaDialog(
    area: BuildingArea?,
    onDismiss: () -> Unit,
    onSave: (BuildingArea) -> Unit
) {
    val isEditing = area != null

    var areaName by remember { mutableStateOf(area?.name ?: "") }
    var areaDescription by remember { mutableStateOf(area?.description ?: "") }
    var enableTiltAnalysis by remember { mutableStateOf(area?.requiresStructuralTilt ?: false) }

    val quickSuggestions = listOf(
        "Front Porch",
        "Roof",
        "Backyard",
        "Side Entrance",
        "Foundation",
        "Windows",
        "Garage",
        "Deck/Patio"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isEditing) "Edit Area" else "Create Area",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Area Name
                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    label = { Text("Area Name") },
                    placeholder = { Text("e.g., Front Porch, Roof, Backyard") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Home, contentDescription = null)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Quick Suggestions Section
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quick suggestions:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Grid of suggestion buttons (2 columns)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickSuggestions.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { suggestion ->
                                    SuggestionChip(
                                        text = suggestion,
                                        onClick = { areaName = suggestion },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Add empty space if odd number
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Description
                OutlinedTextField(
                    value = areaDescription,
                    onValueChange = { areaDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Additional notes about this area") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(Modifier.height(16.dp))

                // Structural Tilt Analysis Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableTiltAnalysis) {
                            Color(0xFFEFF6FF)
                        } else {
                            Color(0xFFF9FAFB)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Architecture,
                                        contentDescription = null,
                                        tint = if (enableTiltAnalysis) {
                                            Color(0xFF2563EB)
                                        } else {
                                            Color.Gray
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Structural Tilt Analysis",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    "Enable if assessing structural integrity",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Switch(
                                checked = enableTiltAnalysis,
                                onCheckedChange = { enableTiltAnalysis = it }
                            )
                        }

                        AnimatedVisibility(visible = enableTiltAnalysis) {
                            Column {
                                Spacer(Modifier.height(8.dp))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Preliminary screening only. Results are estimates (±1-2° accuracy). For critical structural concerns, consult a licensed structural engineer.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF92400E),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (areaName.isNotBlank()) {
                                val savedArea = if (isEditing) {
                                    area!!.copy(
                                        name = areaName,
                                        description = areaDescription,
                                        requiresStructuralTilt = enableTiltAnalysis
                                    )
                                } else {
                                    BuildingArea(
                                        id = UUID.randomUUID().toString(),
                                        name = areaName,
                                        description = areaDescription,
                                        requiresStructuralTilt = enableTiltAnalysis
                                    )
                                }
                                onSave(savedArea)
                            }
                        },
                        enabled = areaName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Icon(
                            if (isEditing) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditing) "Save Changes" else "Create Area")
                    }
                }
            }
        }
    }
}

@Composable
fun AreaListScreen(
    areas: List<BuildingArea>,
    onAddPhotoClick: (BuildingArea) -> Unit,
    onEditClick: (BuildingArea) -> Unit,
    onDeleteClick: (BuildingArea) -> Unit,
    onCreateNewArea: () -> Unit,
    onProceedToInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 24.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Building",
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF8B5CF6), CircleShape)
                            .padding(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Organize by Areas",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Text(
                        "Create different areas of your building and capture photos for each section.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }

            // Empty State or Areas List
            if (areas.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFFD1D5DB)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Areas Yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Start by creating your first building area",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(areas) { area ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        AreaCard(
                            area = area,
                            onAddPhotoClick = { onAddPhotoClick(area) },
                            onEditClick = { onEditClick(area) },
                            onDeleteClick = { onDeleteClick(area) }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Add New Area Button
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = onCreateNewArea,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2563EB)
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add New Area",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Proceed Button (fixed at bottom)
        if (areas.isNotEmpty() && areas.all { it.photos.isNotEmpty() }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Ready to Analyze",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF059669)
                            )
                            Text(
                                "${areas.size} areas • ${areas.sumOf { it.photos.size }} photos",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Button(
                        onClick = onProceedToInfo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text(
                            "Proceed to Building Info",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AreaCard(
    area: BuildingArea,
    onAddPhotoClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEDE9FE)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = area.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "${area.photos.size} photo${if (area.photos.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Action Buttons
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Photo Preview Row
            if (area.photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(area.photos) { photoUri ->
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Photo preview",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFE5E7EB),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (area.photos.size > 4) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${area.photos.size - 4}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // Manage Photos Button
            Button(
                onClick = onAddPhotoClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Manage Photos",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

enum class AreaScreen {
    AreaList,
    CameraCapture
}
