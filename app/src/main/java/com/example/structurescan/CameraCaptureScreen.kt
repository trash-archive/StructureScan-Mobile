package com.example.structurescan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraCaptureScreen(
    areaName: String,
    existingPhotos: List<PhotoMetadata> = emptyList(),
    onPhotosSubmitted: (List<PhotoMetadata>, List<PhotoMetadata>) -> Unit,  // ✅ NEW: returns new photos AND deleted photos
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val tiltMonitor = remember { TiltMonitor(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // ✅ Initialize with existing photos
    var capturedPhotos by remember { mutableStateOf(existingPhotos) }
    var deletedPhotos by remember { mutableStateOf<List<PhotoMetadata>>(emptyList()) }  // ✅ NEW: Track deletions
    var showGallery by remember { mutableStateOf(false) }

    var showLocationDialog by remember { mutableStateOf(false) }
    var tempCapturedPhoto by remember { mutableStateOf<Pair<Uri, TiltMeasurement?>?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<PhotoMetadata?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            tiltMonitor.stopListening()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGallery) {
            PhotoGalleryView(
                photos = capturedPhotos,
                onBack = { showGallery = false },
                onDeletePhoto = { photo ->
                    photoToDelete = photo
                    showDeleteDialog = true
                },
                onSubmitPhotos = {
                    // ✅ UPDATED: Submit new photos and deleted photos
                    val newPhotos = capturedPhotos.filterNot { it in existingPhotos }
                    onPhotosSubmitted(newPhotos, deletedPhotos)
                },
                onClearAll = {
                    // ✅ UPDATED: Mark all existing photos as deleted
                    deletedPhotos = existingPhotos
                    capturedPhotos = emptyList()
                    showGallery = false
                }
            )
        } else {
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        setupCamera(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView = this,
                            onImageCaptureReady = { capture ->
                                imageCapture = capture
                            },
                            onCameraProviderReady = { provider ->
                                cameraProvider = provider
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = areaName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (capturedPhotos.isNotEmpty()) {
                        Text(
                            text = "${capturedPhotos.size} photo${if (capturedPhotos.size != 1) "s" else ""}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Placeholder for symmetry
                Spacer(modifier = Modifier.width(48.dp))
            }

            // ✅ Bottom controls
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✅ Gallery button (bottom left)
                    if (capturedPhotos.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { showGallery = true },
                            modifier = Modifier.size(56.dp),
                            containerColor = Color.White.copy(alpha = 0.9f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Photo,
                                    contentDescription = "Gallery",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                // Badge
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-8).dp),
                                    shape = CircleShape,
                                    color = Color(0xFF2563EB)
                                ) {
                                    Text(
                                        text = capturedPhotos.size.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }

                    // ✅ Capture button (center)
                    FloatingActionButton(
                        onClick = {
                            if (isCapturing) return@FloatingActionButton

                            val capture = imageCapture

                            if (capture == null) {
                                Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                                return@FloatingActionButton
                            }

                            isCapturing = true
                            tiltMonitor.startListening()

                            kotlinx.coroutines.GlobalScope.launch {
                                kotlinx.coroutines.delay(200)

                                val tiltAtCapture = tiltMonitor.getCurrentTilt()

                                capturePhoto(
                                    context = context,
                                    imageCapture = capture,
                                    tiltMeasurement = tiltAtCapture,
                                    onPhotoCaptured = { uri, measurement ->
                                        isCapturing = false
                                        tiltMonitor.stopListening()
                                        Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()

                                        tempCapturedPhoto = Pair(uri, measurement)
                                        showLocationDialog = true
                                    },
                                    onError = { error ->
                                        isCapturing = false
                                        tiltMonitor.stopListening()
                                        Toast.makeText(context, "Capture failed: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = if (!isCapturing) Color.White else Color.Gray,
                        shape = CircleShape
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // ✅ UPDATED: Save/Submit button - show if there are new photos OR deletions
                    if (capturedPhotos.size > existingPhotos.size || deletedPhotos.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                val newPhotos = capturedPhotos.filterNot { it in existingPhotos }
                                onPhotosSubmitted(newPhotos, deletedPhotos)
                            },
                            modifier = Modifier.size(56.dp),
                            containerColor = Color(0xFF059669),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }

    // Location naming dialog
    if (showLocationDialog && tempCapturedPhoto != null) {
        PhotoLocationDialog(
            onDismiss = {
                val (uri, tilt) = tempCapturedPhoto!!
                capturedPhotos = capturedPhotos + PhotoMetadata(uri = uri, tilt = tilt)
                showLocationDialog = false
                tempCapturedPhoto = null
            },
            onSave = { locationName ->
                val (uri, tilt) = tempCapturedPhoto!!
                capturedPhotos = capturedPhotos + PhotoMetadata(
                    uri = uri,
                    locationName = locationName,
                    tilt = tilt
                )
                showLocationDialog = false
                tempCapturedPhoto = null
            }
        )
    }

    // ✅ UPDATED: Delete confirmation dialog
    if (showDeleteDialog && photoToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                photoToDelete = null
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
                    "Delete Photo?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this photo? This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // ✅ NEW: Track if it's an existing photo being deleted
                        if (photoToDelete in existingPhotos) {
                            deletedPhotos = deletedPhotos + photoToDelete!!
                        }
                        capturedPhotos = capturedPhotos.filter { it != photoToDelete }
                        showDeleteDialog = false
                        photoToDelete = null
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
                    photoToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ✅ Photo Gallery View
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryView(
    photos: List<PhotoMetadata>,
    onBack: () -> Unit,
    onDeletePhoto: (PhotoMetadata) -> Unit,
    onSubmitPhotos: () -> Unit,
    onClearAll: () -> Unit
) {
    var showClearAllDialog by remember { mutableStateOf(false) }
    var selectedPhoto by remember { mutableStateOf<PhotoMetadata?>(null) }

    Scaffold(
        topBar = {
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
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back to Camera",
                            tint = Color.Black
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Captured Photos",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "${photos.size} photo${if (photos.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    if (photos.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // ✅ UPDATED: Always show save button when there are photos
            if (photos.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Button(
                        onClick = onSubmitPhotos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Save All Photos",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (photos.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFFD1D5DB)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Photos Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Captured photos will appear here",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Photo grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8FAFC)),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(photos) { photo ->
                    PhotoGridItem(
                        photo = photo,
                        onClick = { selectedPhoto = photo },
                        onDelete = { onDeletePhoto(photo) }
                    )
                }
            }
        }
    }

    // Clear all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
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
                    "Clear All Photos?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete all ${photos.size} photo${if (photos.size != 1) "s" else ""}? This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Photo detail view dialog
    if (selectedPhoto != null) {
        Dialog(onDismissRequest = { selectedPhoto = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Photo Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedPhoto = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Photo
                    AsyncImage(
                        model = selectedPhoto!!.uri,
                        contentDescription = "Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.Black),
                        contentScale = ContentScale.Fit
                    )

                    // Details
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (selectedPhoto!!.locationName.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF2563EB)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    selectedPhoto!!.locationName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (selectedPhoto!!.tilt != null) {
                            val tilt = selectedPhoto!!.tilt!!
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Architecture,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF6B7280)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Pitch: ${tilt.pitch.format(1)}° | Roll: ${tilt.roll.format(1)}°",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Delete button
                        OutlinedButton(
                            onClick = {
                                onDeletePhoto(selectedPhoto!!)
                                selectedPhoto = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFDC2626)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Photo")
                        }
                    }
                }
            }
        }
    }
}

// ✅ Photo Grid Item
@Composable
fun PhotoGridItem(
    photo: PhotoMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Delete button overlay
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Location name badge
            if (photo.locationName.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = photo.locationName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// Photo Location Dialog
@Composable
fun PhotoLocationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var locationName by remember { mutableStateOf("") }

    val quickSuggestions = listOf(
        "North Wall", "South Wall", "East Wall", "West Wall",
        "Corner", "Center", "Entry Point", "Exit Point"
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
                        "Name Photo Location",
                        fontSize = 18.sp,
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

                Spacer(Modifier.height(8.dp))

                Text(
                    "Optional: Add a specific location name for this photo",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Specific Location") },
                    placeholder = { Text("e.g., Northwest Corner, Entry Door") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    }
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Quick suggestions:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                                    onClick = { locationName = suggestion },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
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
                        Text("Skip", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(locationName) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (locationName.isEmpty()) "Continue" else "Save Location")
                    }
                }
            }
        }
    }
}

// Helper functions
private fun setupCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraProviderReady: (ProcessCameraProvider) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val provider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(Size(2048, 1536))
                .setJpegQuality(90)
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            onImageCaptureReady(imageCapture)
            onCameraProviderReady(provider)

            Log.d("CameraSetup", "Camera bound successfully")
        } catch (e: Exception) {
            Log.e("CameraSetup", "Binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    tiltMeasurement: TiltMeasurement?,
    onPhotoCaptured: (Uri, TiltMeasurement?) -> Unit,
    onError: (String) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d("CameraCapture", "Photo saved to: ${photoFile.absolutePath}")
                try {
                    val compressedUri = compressImageIfNeeded(photoFile, maxSizeMB = 2.0f)
                    onPhotoCaptured(compressedUri, tiltMeasurement)
                } catch (e: Exception) {
                    Log.e("CameraCapture", "Compression failed", e)
                    onError("Failed to process image")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Capture failed: ${exception.message}", exception)
                onError(exception.message ?: "Unknown error")
            }
        }
    )
}

private fun compressImageIfNeeded(file: File, maxSizeMB: Float): Uri {
    val fileSizeMB = file.length() / (1024f * 1024f)

    if (fileSizeMB <= maxSizeMB) {
        return Uri.fromFile(file)
    }

    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    val quality = ((maxSizeMB / fileSizeMB) * 90).toInt().coerceIn(60, 90)

    val outputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    outputStream.close()
    bitmap.recycle()

    return Uri.fromFile(file)
}

private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
