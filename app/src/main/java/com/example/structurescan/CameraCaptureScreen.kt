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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.LifecycleOwner
import android.widget.Toast
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraCaptureScreen(
    areaName: String,
    onPhotoCaptured: (Uri, TiltMeasurement?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ✅ Create TiltMonitor but DON'T start continuous listening
    val tiltMonitor = remember { TiltMonitor(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // ✅ Only start/stop sensor when screen is active
    DisposableEffect(Unit) {
        onDispose {
            tiltMonitor.stopListening()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview only - no overlays
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    setupCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        onImageCaptureReady = { capture ->
                            imageCapture = capture
                            Log.d("CameraSetup", "ImageCapture ready")
                        },
                        onCameraProviderReady = { provider ->
                            cameraProvider = provider
                            Log.d("CameraSetup", "CameraProvider ready")
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Simple top bar - no tilt indicators
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

            Text(
                text = areaName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(48.dp))
        }

        // Simple bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ Simple capture button - no quality checks
            FloatingActionButton(
                onClick = {
                    if (isCapturing) return@FloatingActionButton

                    val capture = imageCapture

                    if (capture == null) {
                        Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                        Log.e("CameraCapture", "ImageCapture is null")
                        return@FloatingActionButton
                    }

                    isCapturing = true
                    Log.d("CameraCapture", "Capturing photo...")

                    // ✅ Start sensors ONLY when capture button pressed
                    tiltMonitor.startListening()

                    // ✅ Small delay to let sensors stabilize
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(200) // Give sensors time to initialize

                        val tiltAtCapture = tiltMonitor.getCurrentTilt()

                        Log.d("CameraCapture",
                            "Tilt at capture: ${tiltAtCapture?.let {
                                "pitch=${it.pitch.format(1)}°, roll=${it.roll.format(1)}°"
                            } ?: "not available"}"
                        )

                        capturePhoto(
                            context = context,
                            imageCapture = capture,
                            tiltMeasurement = tiltAtCapture,
                            onPhotoCaptured = { uri, measurement ->
                                isCapturing = false
                                tiltMonitor.stopListening() // ✅ Stop sensors after capture
                                Log.d("CameraCapture", "Photo captured: $uri")
                                Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                                onPhotoCaptured(uri, measurement)
                            },
                            onError = { error ->
                                isCapturing = false
                                tiltMonitor.stopListening() // ✅ Stop sensors on error
                                Log.e("CameraCapture", "Capture failed: $error")
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
        }
    }
}

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

// Helper extension
private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
