package com.example.structurescan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

/**
 * Quick Tilt Analysis Activity
 * Purpose: Fast tilt check without database storage
 * Flow: Camera → Capture → Analyze → Show Results
 */
class QuickTiltAnalysisActivity : ComponentActivity() {

    private lateinit var tiltMonitor: TiltMonitor
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tiltMonitor = TiltMonitor(this)
        cameraExecutor = ContextCompat.getMainExecutor(this)

        setContent {
            MaterialTheme {
                QuickTiltAnalysisScreen(
                    tiltMonitor = tiltMonitor,
                    onImageCaptureReady = { capture -> imageCapture = capture },
                    onCapturePhoto = { capturePhoto() },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tiltMonitor.startListening()
    }

    override fun onPause() {
        super.onPause()
        tiltMonitor.stopListening()
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val photoFile = File(externalCacheDir, "quick_tilt_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Device tilt at capture moment
        val cameraTilt = tiltMonitor.getCurrentTilt()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("QuickTilt", "Photo saved: ${photoFile.absolutePath}")
                    analyzePhoto(photoFile, cameraTilt)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("QuickTilt", "Photo capture failed", exc)
                    runOnUiThread {
                        Toast.makeText(
                            this@QuickTiltAnalysisActivity,
                            "Failed to capture photo",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun analyzePhoto(photoFile: File, cameraTilt: TiltMeasurement?) {
        runOnUiThread {
            Toast.makeText(this, "Analyzing tilt...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                val rotatedBitmap = correctImageRotation(bitmap, photoFile.absolutePath)

                val analyzer = StructuralTiltAnalyzer()
                val result = analyzer.analyzeWithCompensation(rotatedBitmap, cameraTilt)

                rotatedBitmap.recycle()
                photoFile.delete()

                runOnUiThread {
                    val intent = Intent(this, QuickTiltResultActivity::class.java).apply {
                        putExtra("VERTICAL_TILT", result.averageVerticalTilt)
                        putExtra("HORIZONTAL_TILT", result.averageHorizontalTilt)
                        putExtra("CONFIDENCE", result.confidence)
                        putExtra("DETECTED_LINES", result.detectedLines)
                        putExtra("SEVERITY", result.tiltSeverity.name)
                        putExtra("CAMERA_COMPENSATION", result.cameraTiltCompensation ?: 0f)
                        putExtra("RAW_VERTICAL", result.rawVerticalTilt ?: 0f)
                        putExtra("RAW_HORIZONTAL", result.rawHorizontalTilt ?: 0f)
                        putExtra("WARNING", result.warning)
                    }
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("QuickTilt", "Analysis failed", e)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Analysis failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun correctImageRotation(bitmap: Bitmap, path: String): Bitmap {
        val exif = androidx.exifinterface.media.ExifInterface(path)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

@Composable
fun QuickTiltAnalysisScreen(
    tiltMonitor: TiltMonitor,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCapturePhoto: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Real-time tilt
    var currentTilt by remember { mutableStateOf<TiltMeasurement?>(null) }

    // New: guidance states
    var showInfoBanner by remember { mutableStateOf(true) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTilt = tiltMonitor.getCurrentTilt()
            kotlinx.coroutines.delay(120) // ~8 fps
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Camera preview
            CameraPreview(
                onImageCaptureReady = onImageCaptureReady,
                modifier = Modifier.fillMaxSize()
            )

            // Top bar with info icon
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Quick Tilt Analysis",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "How it works",
                            tint = Color.White
                        )
                    }
                }
            }

            // Inline “how it works” banner
            if (showInfoBanner) {
                Surface(
                    color = Color(0xF21F2937),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp)
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
                                "What Quick Tilt does",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Point at a wall or column, keep the phone level, then capture. The app measures how far the structure leans from vertical.",
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

            // Live tilt indicator
            currentTilt?.let { tilt ->
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 132.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val magnitude = tilt.tiltMagnitude
                        val color = when {
                            magnitude <= 3f -> Color(0xFF4CAF50)
                            magnitude <= 5f -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                        Text(
                            text = "Device tilt: ${String.format("%.1f°", magnitude)}",
                            color = color,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                magnitude <= 3f -> "Good – phone is level enough"
                                magnitude <= 5f -> "OK – try to level a bit more"
                                else -> "Tilted – straighten your phone for a better reading"
                            },
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Instructions card
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "How to capture",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• Point at a vertical element (wall, column, façade).\n" +
                                "• Stand back enough to see the full height.\n" +
                                "• Hold the phone as level as possible before capturing.",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Capture button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                IconButton(
                    onClick = {
                        scope.launch { onCapturePhoto() }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Capture",
                        tint = Color(0xFF0288D1),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

        } else {
            // Permission denied screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera Permission Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please grant camera permission to use Quick Tilt Analysis.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }

    // Help dialog
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
            title = { Text("What is Quick Tilt?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick Tilt Analysis helps you estimate how much a wall or column is leaning using a single photo and your device sensors."
                    )
                    Text("How it works:", fontWeight = FontWeight.SemiBold)
                    Text("• The camera detects vertical edges and measures their tilt in degrees.")
                    Text("• Your phone’s tilt is subtracted so only the structure’s tilt remains.")
                    Text("• Results are grouped into None, Minor, Moderate, or Severe tilt.")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This is a quick screening tool. For any moderate or severe tilt, always consult a licensed structural engineer.",
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

@Composable
fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            onImageCaptureReady(imageCapture)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("QuickTilt", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
