package com.example.structurescan
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class BuildingArea(
    val id: String,
    var name: String,
    val photos: MutableList<Uri> = mutableListOf()
) : Parcelable

class BuildingAreaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }

        setContent {
            MaterialTheme {
                BuildingAreasScreen(
                    assessmentName = assessmentName,
                    onBack = { finish() },
                    onProceed = { areasList ->
                        val areaWithNoPhotos = areasList.find { it.photos.size < 2 }
                        if (areaWithNoPhotos != null) {
                            Toast.makeText(
                                this@BuildingAreaActivity,
                                "No images found for ${areaWithNoPhotos.name}. Please add at least 2 photos.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            // Only navigate if validation passes
                            val intent = Intent(this, BuildingInfoActivity::class.java)
                            intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                            intent.putParcelableArrayListExtra(IntentKeys.BUILDING_AREAS, ArrayList(areasList))
                            startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BuildingAreasScreen(
    assessmentName: String,
    onBack: () -> Unit,
    onProceed: (List<BuildingArea>) -> Unit
) {
    val context = LocalContext.current
    val areas = remember { mutableStateListOf<BuildingArea>() }
    var showAddAreaDialog by remember { mutableStateOf(false) }
    var selectedAreaId by remember { mutableStateOf<String?>(null) }
    var editingArea by remember { mutableStateOf<BuildingArea?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val totalPhotos = areas.sumOf { it.photos.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "Building Areas",
                color = Color(0xFF2563EB),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
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
                "Create different areas of your building and capture photos\nfor each section.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Areas", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "${areas.size}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Photos", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "$totalPhotos",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                IconButton(
                    onClick = { /* Quick camera access */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2563EB), CircleShape)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add New Area Button
            OutlinedButton(
                onClick = {
                    if (areas.size >= 10) {
                        Toast.makeText(
                            context,
                            "Maximum 10 areas allowed",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showAddAreaDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF2563EB)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add New Area")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Areas List
        if (areas.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No areas created yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    "Start by adding your first building area",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(areas, key = { it.id }) { area ->
                    AreaCard(
                        area = area,
                        refreshTrigger = refreshTrigger,
                        onPhotoClick = { selectedAreaId = area.id },
                        onEdit = { editingArea = area },
                        onDelete = { areas.remove(area) }
                    )
                }
            }
        }

        // Bottom Button
        Button(
            onClick = {
                if (areas.isEmpty() || totalPhotos == 0) {
                    Toast.makeText(
                        context,
                        "Add at least one area with photos to proceed",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onProceed(areas.toList())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = areas.isNotEmpty() && totalPhotos > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10B981),
                disabledContainerColor = Color.LightGray
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Proceed to Analysis", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }

    // Add Area Dialog
    if (showAddAreaDialog) {
        AddAreaDialog(
            onDismiss = { showAddAreaDialog = false },
            onConfirm = { areaName ->
                areas.add(
                    BuildingArea(
                        id = System.currentTimeMillis().toString(),
                        name = areaName
                    )
                )
                showAddAreaDialog = false
            }
        )
    }

    // Edit Area Dialog
    editingArea?.let { area ->
        EditAreaDialog(
            currentName = area.name,
            onDismiss = { editingArea = null },
            onConfirm = { newName ->
                area.name = newName
                editingArea = null
                refreshTrigger++
            }
        )
    }

    // Camera Screen for Selected Area
    selectedAreaId?.let { areaId ->
        val area = areas.find { it.id == areaId }
        area?.let {
            CameraScreenForArea(
                area = it,
                onBack = {
                    selectedAreaId = null
                    refreshTrigger++ // Force refresh when returning
                }
            )
        }
    }
}

@Composable
fun AreaCard(
    area: BuildingArea,
    refreshTrigger: Int,
    onPhotoClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Force recomposition when refreshTrigger changes
    val photoCount by remember(refreshTrigger, area.photos.size) {
        mutableStateOf(area.photos.size)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPhotoClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            area.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            "$photoCount ${if (photoCount == 1) "photo" else "photos"}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (photoCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    area.photos.take(3).forEach { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color.LightGray, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (photoCount > 3) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+${photoCount - 3}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPhotoClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Photos")
                }
            }
        }
    }
}

@Composable
fun AddAreaDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var areaName by remember { mutableStateOf("") }
    var selectedSuggestion by remember { mutableStateOf<String?>(null) }

    val suggestions = listOf(
        "Roof", "Foundation", "Walls", "Floors", "Ceiling",
        "Windows", "Doors", "Stairs", "Basement", "Attic"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Add New Area",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Area Name",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = areaName,
                    onValueChange = {
                        areaName = it
                        selectedSuggestion = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., Roof, Foundation, Walls", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Quick Suggestions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.take(3).forEach { suggestion ->
                        SuggestionChip(
                            text = suggestion,
                            isSelected = selectedSuggestion == suggestion,
                            onClick = {
                                areaName = suggestion
                                selectedSuggestion = suggestion
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.drop(3).take(3).forEach { suggestion ->
                        SuggestionChip(
                            text = suggestion,
                            isSelected = selectedSuggestion == suggestion,
                            onClick = {
                                areaName = suggestion
                                selectedSuggestion = suggestion
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (areaName.isNotBlank()) {
                                onConfirm(areaName)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = areaName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Area")
                    }
                }
            }
        }
    }
}

@Composable
fun EditAreaDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var areaName by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Edit Area Name",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Area Name",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (areaName.isNotBlank()) {
                                onConfirm(areaName)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = areaName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF2563EB) else Color(0xFFF3F4F6),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = if (isSelected) Color.White else Color.Black
        )
    }
}

@Composable
fun CameraScreenForArea(area: BuildingArea, onBack: () -> Unit) {
    val context = LocalContext.current
    var currentImageIndex by remember { mutableStateOf(if (area.photos.isNotEmpty()) area.photos.size - 1 else 0) }
    var photoCount by remember { mutableStateOf(area.photos.size) }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingPhotoUri != null) {
                area.photos.add(pendingPhotoUri!!)
                currentImageIndex = area.photos.size - 1
                photoCount = area.photos.size // Update local state
            }
            pendingPhotoUri = null
        }

    val capturePhoto = {
        if (area.photos.size >= 15) {
            Toast.makeText(context, "Max 15 photos per area", Toast.LENGTH_SHORT).show()
        } else {
            val photoFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "photo_${System.currentTimeMillis()}.jpg"
            )
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", photoFile
            )
            pendingPhotoUri = uri
            takePicture.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Column {
                Text(
                    area.name,
                    color = Color.Black,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$photoCount/15 photos",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // Image Preview with Swipe Gesture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF9FAFB))
        ) {
            if (photoCount == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(200.dp)
                        .clickable { capturePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.Gray,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(20f, 20f)
                                )
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No photos yet",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            "Tap the camera button below to start",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 50 && currentImageIndex > 0) {
                                    currentImageIndex--
                                } else if (dragAmount < -50 && currentImageIndex < area.photos.size - 1) {
                                    currentImageIndex++
                                }
                            }
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(area.photos[currentImageIndex]),
                        contentDescription = "Captured Image ${currentImageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Image counter badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${currentImageIndex + 1} / $photoCount",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Navigation arrows
                    if (photoCount > 1) {
                        if (currentImageIndex > 0) {
                            IconButton(
                                onClick = { currentImageIndex-- },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(16.dp)
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        if (currentImageIndex < photoCount - 1) {
                            IconButton(
                                onClick = { currentImageIndex++ },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(16.dp)
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Delete button
                        IconButton(
                            onClick = {
                                area.photos.removeAt(currentImageIndex)
                                photoCount = area.photos.size
                                if (area.photos.isNotEmpty()) {
                                    currentImageIndex = (currentImageIndex - 1).coerceAtLeast(0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(48.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capture Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = capturePhoto,
                        modifier = Modifier
                            .size(70.dp)
                            .background(
                                if (photoCount < 15) Color(0xFF2563EB) else Color.Gray,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Capture",
                            tint = Color.White,
                            modifier = Modifier.size(35.dp)
                        )
                    }
                    Text(
                        "Capture",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Done Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Done",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        "Done",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}