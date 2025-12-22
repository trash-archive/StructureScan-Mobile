package com.example.structurescan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QuickScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QuickScanCameraScreen(onAnalyze = ::analyzePhotos)
            }
        }
    }

    private fun analyzePhotos(uris: List<String>) {
        val intent = Intent(this, QuickScanResultsActivity::class.java).apply {
            putStringArrayListExtra("QUICK_SCAN_URIS", ArrayList(uris))
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScanCameraScreen(onAnalyze: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGallery by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<String?>(null) }

    // New: onboarding / help states
    var showInfoBanner by remember { mutableStateOf(true) }
    var showHelpDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGallery) {
            QuickScanGalleryView(
                photos = capturedPhotos,
                onBack = { showGallery = false },
                onDeletePhoto = { uri ->
                    photoToDelete = uri
                    showDeleteDialog = true
                },
                onAnalyze = onAnalyze
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
                            onImageCaptureReady = { capture -> imageCapture = capture },
                            onCameraProviderReady = { provider -> cameraProvider = provider }
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
                IconButton(onClick = { (context as ComponentActivity).finish() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Quick Scan",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (capturedPhotos.isNotEmpty()) {
                        Text(
                            text = "${capturedPhotos.size} photos",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(Icons.Default.Info, contentDescription = "How it works", tint = Color.White)
                }
            }

            // Inline info banner (on top of preview)
            if (showInfoBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        color = Color(0xF21F2937),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TipsAndUpdates,
                                contentDescription = null,
                                tint = Color(0xFFFEF3C7),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "How Quick Scan works",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Take clear photos of walls and surfaces. The AI will highlight possible damage and suggest what to do next.",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = { showInfoBanner = false }) {
                                Text("Got it", color = Color(0xFFBFDBFE), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Bottom controls
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
                    // Gallery button
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

                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            if (isCapturing) return@FloatingActionButton
                            val capture = imageCapture
                            if (capture == null) {
                                Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                                return@FloatingActionButton
                            }

                            isCapturing = true
                            scope.launch {
                                capturePhoto(
                                    context = context,
                                    imageCapture = capture,
                                    onPhotoCaptured = { uri ->
                                        isCapturing = false
                                        capturedPhotos = capturedPhotos + uri.toString()
                                        Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { error ->
                                        isCapturing = false
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

                    // Analyze button
                    if (capturedPhotos.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { onAnalyze(capturedPhotos) },
                            modifier = Modifier.size(56.dp),
                            containerColor = Color(0xFF059669),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = "Analyze",
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

    // Delete confirmation dialog
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
            title = { Text("Delete Photo?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this photo?") },
            confirmButton = {
                Button(
                    onClick = {
                        capturedPhotos = capturedPhotos.filter { it != photoToDelete }
                        showDeleteDialog = false
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
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

    // Help dialog – explains purpose and flow
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("What is Quick Scan?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick Scan helps you capture photos of building surfaces and uses AI to highlight possible issues like cracks, spalling, or paint damage."
                    )
                    Text("How to use:", fontWeight = FontWeight.SemiBold)
                    Text("• Take 1 or more clear photos of the area you want to check.")
                    Text("• Tap Analyze to run the AI assessment.")
                    Text("• Review the issues and follow the recommended actions.")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Note: Results are estimates only. For serious or high‑risk findings, always consult a licensed engineer or building professional.",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Gallery
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScanGalleryView(
    photos: List<String>,
    onBack: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    onAnalyze: (List<String>) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Camera", tint = Color.Black)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Captured Photos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("${photos.size} photos", fontSize = 12.sp, color = Color.Gray)
                    }

                    IconButton(onClick = { onAnalyze(photos) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Analyze", tint = Color(0xFF059669))
                    }
                }
            }
        }
    ) { paddingValues ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFFD1D5DB))
                    Spacer(Modifier.height(16.dp))
                    Text("No Photos Yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(photos) { uri ->
                    QuickScanPhotoGridItem(
                        uri = uri,
                        onClick = { },
                        onDelete = { onDeletePhoto(uri) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickScanPhotoGridItem(
    uri: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            AsyncImage(
                model = uri,
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Camera helpers
// -----------------------------------------------------------------------------

private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
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
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(Size(2048, 1536))
                .setJpegQuality(90)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)

            onImageCaptureReady(imageCapture)
            onCameraProviderReady(provider)
        } catch (e: Exception) {
            Log.e("CameraSetup", "Binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoCaptured: (Uri) -> Unit,
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
                val uri = compressImageIfNeeded(photoFile, 2.0f)
                onPhotoCaptured(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "Unknown error")
            }
        }
    )
}

private fun compressImageIfNeeded(file: File, maxSizeMB: Float): Uri {
    val fileSizeMB = file.length() / (1024f * 1024f)
    if (fileSizeMB <= maxSizeMB) return Uri.fromFile(file)

    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    val quality = ((maxSizeMB / fileSizeMB) * 90).toInt().coerceIn(60, 90)

    val outputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    outputStream.close()
    bitmap.recycle()

    return Uri.fromFile(file)
}
