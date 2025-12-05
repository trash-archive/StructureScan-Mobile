package com.example.structurescan
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File

class CaptureImagesActivity : ComponentActivity() {
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
                CameraScreen(
                    assessmentName = assessmentName,
                    onBack = {
                        finish()
                    },
                    onProceed = { images ->
                        val intent = Intent(this, BuildingInfoActivity::class.java)
                        intent.putStringArrayListExtra(
                            IntentKeys.FINAL_IMAGES,
                            ArrayList(images.map { it.toString() })
                        )
                        intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun CameraScreen(
    assessmentName: String,
    onBack: () -> Unit,
    onProceed: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<Uri>() }
    var currentImageIndex by remember { mutableStateOf(0) }
    var showInstructions by remember { mutableStateOf(true) }

    // Activity Result Launcher
    val savedPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val updatedImagesStrings = result.data?.getStringArrayListExtra(IntentKeys.UPDATED_IMAGES)
            if (updatedImagesStrings != null) {
                images.clear()
                images.addAll(updatedImagesStrings.map { Uri.parse(it) })
                currentImageIndex = if (images.isNotEmpty()) images.size - 1 else 0
            }
        }
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingPhotoUri != null) {
                images.add(pendingPhotoUri!!)
                currentImageIndex = images.size - 1
            }
            pendingPhotoUri = null
        }

    val capturePhoto = {
        if (images.size >= 7) {
            Toast.makeText(context, "Max 7 photos allowed", Toast.LENGTH_SHORT).show()
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

    // ✅ Instruction Dialog
    if (showInstructions) {
        InstructionDialog(onDismiss = { showInstructions = false })
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
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "Capture Photos • ${images.size}/7",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // ✅ Image Preview with Swipe Gesture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
        ) {
            if (images.isEmpty()) {
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
                    Text(
                        text = "+",
                        color = Color.Gray,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            } else {
                // Image with swipe gesture
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 50 && currentImageIndex > 0) {
                                    currentImageIndex--
                                } else if (dragAmount < -50 && currentImageIndex < images.size - 1) {
                                    currentImageIndex++
                                }
                            }
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(images[currentImageIndex]),
                        contentDescription = "Captured Image ${currentImageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // ✅ Image counter badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${currentImageIndex + 1} / ${images.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ✅ Swipe hint (only show when multiple images)
                    if (images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwipeLeft,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Swipe to view photos",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Icon(
                                Icons.Default.SwipeRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
            if (images.isNotEmpty()) {
                Text(
                    text = "${images.size} photo${if (images.size != 1) "s" else ""} captured",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SavedPhotoActivity::class.java)
                            intent.putStringArrayListExtra(
                                IntentKeys.CAPTURED_IMAGES,
                                ArrayList(images.map { it.toString() })
                            )
                            intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                            savedPhotoLauncher.launch(intent)
                        },
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Saved Photos",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text("Gallery", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }

                // Capture Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = capturePhoto,
                        modifier = Modifier
                            .size(70.dp)
                            .background(
                                if (images.size < 7) Color(0xFF2563EB) else Color.Gray,
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
                }

                // Proceed Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            when {
                                images.isEmpty() -> {
                                    Toast.makeText(context, "Please capture an image", Toast.LENGTH_SHORT).show()
                                }
                                images.size in 1..2 -> {
                                    Toast.makeText(
                                        context,
                                        "It's recommended to upload at least 3 photos for better analysis.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onProceed(images)
                                }
                                else -> {
                                    onProceed(images)
                                }
                            }
                        },
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Proceed",
                            tint = if (images.isNotEmpty()) Color(0xFF10B981) else Color.Gray,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text("Proceed", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ✅ Instruction Dialog Component
@Composable
fun InstructionDialog(onDismiss: () -> Unit) {
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
                    "How to Capture Photos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InstructionItem(
                        icon = Icons.Default.CameraAlt,
                        text = "Tap the camera button to capture photos"
                    )
                    InstructionItem(
                        icon = Icons.Default.SwipeLeft,
                        text = "Swipe left or right to view captured photos"
                    )
                    InstructionItem(
                        icon = Icons.Default.PhotoLibrary,
                        text = "Tap Gallery to review and delete photos"
                    )
                    InstructionItem(
                        icon = Icons.Default.Check,
                        text = "Tap the checkmark when ready to proceed"
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Got it!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InstructionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text,
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Start
        )
    }
}

@Preview
@Composable
fun CameraPreview() {
    MaterialTheme {
        CameraScreen(
            assessmentName = "Sample Assessment",
            onBack = {},
            onProceed = {}
        )
    }
}