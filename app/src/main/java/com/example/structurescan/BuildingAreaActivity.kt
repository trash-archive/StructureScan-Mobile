package com.example.structurescan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.util.*

class BuildingAreaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"

        // ⭐ CRITICAL FIX: Load building areas from intent if they exist
        val savedBuildingAreas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, BuildingArea::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(IntentKeys.BUILDING_AREAS)
        } ?: arrayListOf()

        setContent {
            MaterialTheme {
                BuildingAreaScreen(
                    assessmentName = assessmentName,
                    initialBuildingAreas = savedBuildingAreas,  // ⭐ PASS TO COMPOSABLE

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
    initialBuildingAreas: List<BuildingArea> = emptyList(),  // ⭐ NEW PARAMETER
    onBackClick: () -> Unit,
    onProceedToInfo: (List<BuildingArea>) -> Unit
) {
    var buildingAreas by remember { mutableStateOf(initialBuildingAreas) }
    var currentScreen by remember { mutableStateOf<AreaScreen>(AreaScreen.AreaList) }
    var selectedArea by remember { mutableStateOf<BuildingArea?>(null) }
    var showAreaDialog by remember { mutableStateOf(false) }
    var areaToEdit by remember { mutableStateOf<BuildingArea?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var areaToDelete by remember { mutableStateOf<BuildingArea?>(null) }
    var showInstructions by remember { mutableStateOf(true) }

    // Instruction Dialog
    if (showInstructions) {
        AreaInstructionDialog(onDismiss = { showInstructions = false })
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (currentScreen) {
            AreaScreen.AreaList -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ✅ UPDATED: Top bar with assessment name in blue
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = assessmentName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB), // Changed to blue
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.Black
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White
                        )
                    )

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
            }

            AreaScreen.CameraCapture -> {
                selectedArea?.let { area ->
                    CameraCaptureScreen(
                        areaName = area.name,
                        existingPhotos = area.photoMetadata,
                        onPhotosSubmitted = { newPhotos, deletedPhotos ->  // ✅ UPDATED: Handle both
                            // Remove deleted photos and add new photos
                            val updatedPhotoList = (area.photoMetadata - deletedPhotos.toSet()) + newPhotos
                            val updatedArea = area.copy(
                                photoMetadata = updatedPhotoList
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

    if (showAreaDialog) {
        AreaDialog(
            area = areaToEdit,
            onDismiss = {
                showAreaDialog = false
                areaToEdit = null
            },
            onSave = { area ->
                if (areaToEdit == null) {
                    buildingAreas = buildingAreas + area
                } else {
                    buildingAreas = buildingAreas.map {
                        if (it.id == area.id) area else it
                    }
                }
                showAreaDialog = false
                areaToEdit = null
            }
        )
    }

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

// ✅ UPDATED: Instruction Dialog with blue theme
@Composable
fun AreaInstructionDialog(onDismiss: () -> Unit) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Instructions",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Organize by Building Areas",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    textAlign = TextAlign.Center
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AreaInstructionItem(
                        icon = Icons.Default.Add,
                        text = "Create different areas of your building e.g., Front Porch, Roof"
                    )
                    AreaInstructionItem(
                        icon = Icons.Default.CameraAlt,
                        text = "Capture photos for each area separately"
                    )
                    AreaInstructionItem(
                        icon = Icons.Default.Architecture,
                        text = "Optional: Turn on tilt check to detect slanted walls or uneven floors"
                    )
                    // NEW DISCLAIMER ITEM - ADD THIS
                    AreaInstructionItem(
                        icon = Icons.Default.Warning,
                        text = "IMPORTANT: Create ALL areas of your home/structure so the overall building can be evaluated properly. Every area is checked for comprehensive analysis."
                    )
                    AreaInstructionItem(
                        icon = Icons.Default.Check,
                        text = "Review all areas before proceeding to building info"
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text(
                        "Got it!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// ✅ UPDATED: Instruction Item with blue icons
@Composable
fun AreaInstructionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF2563EB), // Changed to blue
            modifier = Modifier.size(24.dp)
        )
        Text(
            text,
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Start,
            lineHeight = 20.sp
        )
    }
}

// ✅ UPDATED: Area Dialog with improved design
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

                // ✅ UPDATED: Removed leading icon from Area Name field
                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    label = { Text("Area Name") },
                    placeholder = { Text("e.g., Front Porch, Roof, Backyard") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // ✅ UPDATED: More compact suggestion chips
                Text(
                    text = "Quick suggestions:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Display in a more compact grid
                quickSuggestions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { suggestion ->
                            SuggestionChip(
                                text = suggestion,
                                onClick = { areaName = suggestion },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = areaDescription,
                    onValueChange = { areaDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Additional notes about this area") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                // Around line 155-200 in your AreaDialog
                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableTiltAnalysis) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize(
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                    ) {
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
                                        tint = if (enableTiltAnalysis) Color(0xFF2563EB) else Color.Gray,
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
                                    "Check if walls or floors look slanted",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Switch(
                                checked = enableTiltAnalysis,
                                onCheckedChange = { enableTiltAnalysis = it }
                            )
                        }

                        AnimatedVisibility(
                            visible = enableTiltAnalysis,
                            enter = expandVertically(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Top
                            ) + fadeIn(tween(350, delayMillis = 50)),
                            exit = shrinkVertically(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top
                            ) + fadeOut(tween(200))
                        ) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
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
                                            "This feature uses your phone's camera, which isn't as accurate as professional equipment. It's useful for spotting potential issues early, but always consult an inspector with proper tools for important decisions.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF92400E),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

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
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
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

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = onCreateNewArea,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                drawContent()
                                // Draw dashed border
                                val strokeWidth = 2.dp.toPx()
                                val dashWidth = 8.dp.toPx()
                                val dashGap = 6.dp.toPx()

                                drawRoundRect(
                                    color = Color(0xFF2563EB),
                                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                    size = Size(
                                        size.width - strokeWidth,
                                        size.height - strokeWidth
                                    ),
                                    cornerRadius = CornerRadius(8.dp.toPx()),
                                    style = Stroke(
                                        width = strokeWidth,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(dashWidth, dashGap),
                                            0f
                                        )
                                    )
                                )
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2563EB),
                            containerColor = Color.Transparent
                        ),
                        border = null,
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

// ✅ UPDATED: More compact and refined suggestion chip
@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp), // Reduced padding
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp, // Slightly smaller font
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ✅ UPDATED: Enhanced Area Card with better visual hierarchy
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
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp) // Slightly increased elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // ✅ UPDATED: Changed icon background to blue theme
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(10.dp), // Slightly more rounded
                        color = Color(0xFFEFF6FF) // Blue tint
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = Color(0xFF2563EB), // Changed to blue
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = area.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            if (area.requiresStructuralTilt) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = BorderStroke(1.dp, Color(0xFF2563EB))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Architecture,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            "Tilt",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${area.photos.size} photo${if (area.photos.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

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

            // ✅ Show description if exists
            if (area.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = area.description,
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            if (area.photoMetadata.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(area.photoMetadata.take(4)) { photoMeta ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = photoMeta.uri,
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

                            if (photoMeta.locationName.isNotEmpty()) {
                                Text(
                                    text = photoMeta.locationName,
                                    fontSize = 10.sp,
                                    color = Color(0xFF6B7280),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(80.dp)
                                        .padding(top = 4.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (area.photoMetadata.size > 4) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${area.photoMetadata.size - 4}",
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
