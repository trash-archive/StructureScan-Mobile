package com.example.structurescan
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DetailScreen(
                    onSkip = {
                        startActivity(Intent(this, Onboarding1Activity::class.java))
                        finish()
                    },
                    onBack = {
                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    },
                    onContinue = { profession, imageUri, bitmap, onLoadingChange ->
                        uploadProfile(profession, imageUri, bitmap, onLoadingChange)
                    }
                )
            }
        }
    }

    private fun uploadProfile(
        profession: String,
        imageUri: Uri?,
        bitmap: Bitmap?,
        onLoadingChange: (Boolean) -> Unit
    ) {
        onLoadingChange(true)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            onLoadingChange(false)
            Toast.makeText(this, "User not logged in. Please log in first.", Toast.LENGTH_LONG).show()
            return
        }

        val userId = user.uid
        val db = FirebaseFirestore.getInstance()

        // ✅ FLEXIBLE: Check if user has image or profession
        val hasImage = imageUri != null || bitmap != null
        val hasProfession = profession.isNotEmpty()

        if (hasImage) {
            // Upload image first, then save to Firestore
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference.child("profile_images/$userId.jpg")

            val uploadTask = when {
                imageUri != null -> storageRef.putFile(imageUri)
                bitmap != null -> {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    val data = baos.toByteArray()
                    storageRef.putBytes(data)
                }
                else -> null
            }

            uploadTask?.addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Save both profession and photo URL
                    val userData = hashMapOf<String, Any>(
                        "userId" to userId,
                        "photoUrl" to downloadUri.toString()
                    )
                    if (hasProfession) {
                        userData["profession"] = profession
                    }

                    db.collection("users").document(userId)
                        .set(userData, SetOptions.merge())
                        .addOnSuccessListener {
                            onLoadingChange(false)
                            Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, Onboarding1Activity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            onLoadingChange(false)
                            Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }.addOnFailureListener { e ->
                    onLoadingChange(false)
                    Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }?.addOnFailureListener { e ->
                onLoadingChange(false)
                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else if (hasProfession) {
            // ✅ Only save profession without photo
            val userData = hashMapOf<String, Any>(
                "userId" to userId,
                "profession" to profession
            )

            db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    onLoadingChange(false)
                    Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, Onboarding1Activity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    onLoadingChange(false)
                    Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onContinue: (String, Uri?, Bitmap?, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedProfession by remember { mutableStateOf("") }
    var customProfession by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(false) }

    // ✅ Dialog states
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val professionOptions = listOf(
        "Student",
        "Unemployed / Not Working",
        "Engineer",
        "Architect",
        "Inspector",
        "Construction Worker",
        "Technician",
        "Manager",
        "Foreman",
        "Safety Officer",
        "Real Estate Agent",
        "Property Manager",
        "Building Owner",
        "Maintenance Staff",
        "Homeowner",
        "Civil Engineering Graduate",
        "Architecture Graduate",
        "Contractor",
        "Other (Please specify)"
    )

    // Camera Launcher (defined FIRST)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            capturedImage = bitmap
        }
    }

    // Camera Permission Launcher (uses cameraLauncher)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(cameraIntent)
        } else {
            showPermissionDeniedDialog = true
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> imageUri = uri }

    // ✅ Permission Denied Dialog
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("Camera Permission Required") },
            text = { Text("Camera permission is needed to take photos. Please enable it in your device settings.") }
        )
    }

    // ✅ Image Source Dialog (Compose version)
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Choose from Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    // ✅ Request camera permission before launching camera
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("Take Photo")
                }
            },
            title = { Text("Select Option") },
            text = { Text("Choose an image from Gallery or take a photo with Camera.") }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CenterAlignedTopAppBar(
            title = {},
            navigationIcon = {
                IconButton(
                    onClick = { onBack() },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Complete your profile",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "These details are optional and can be updated anytime.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add a profile photo", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .clickable(enabled = !isLoading) {
                            // ✅ Show Compose dialog instead of AlertDialog
                            showImageSourceDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        capturedImage != null -> Image(
                            bitmap = capturedImage!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        imageUri != null -> Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        else -> Text("+", fontSize = 40.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Profession",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { if (!isLoading) isDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProfession,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        placeholder = { Text("e.g. Engineer", color = Color.Gray) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        professionOptions.forEach { profession ->
                            DropdownMenuItem(
                                text = { Text(profession) },
                                onClick = {
                                    selectedProfession = profession
                                    isDropdownExpanded = false
                                    // Show custom input field if "Other (Please specify)" is selected
                                    showCustomInput = profession == "Other (Please specify)"
                                    if (!showCustomInput) {
                                        customProfession = "" // Clear custom input if switching away
                                    }
                                }
                            )
                        }
                    }
                }

                // ✅ Custom profession input field (appears when "Other" is selected)
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customProfession,
                        onValueChange = { customProfession = it },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Please specify your profession", color = Color.Gray) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    // ✅ FLEXIBLE VALIDATION: At least one field must be filled
                    val hasProfession = selectedProfession.isNotEmpty()
                    val hasImage = imageUri != null || capturedImage != null

                    // Validate custom profession input if "Other" is selected
                    if (selectedProfession == "Other (Please specify)" && customProfession.trim().isEmpty()) {
                        Toast.makeText(
                            context,
                            "Please specify your profession",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    if (!hasProfession && !hasImage) {
                        Toast.makeText(
                            context,
                            "Please add a profile photo or select a profession to continue",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    // Use custom profession if "Other" was selected, otherwise use dropdown selection
                    val finalProfession = if (selectedProfession == "Other (Please specify)") {
                        customProfession.trim()
                    } else {
                        selectedProfession
                    }

                    onContinue(finalProfession, imageUri, capturedImage) { loading ->
                        isLoading = loading
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0288D1),
                    disabledContainerColor = Color(0xFF0288D1).copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "CONTINUE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Skip for now",
                color = if (isLoading) Color.Gray else Color(0xFF0288D1),
                modifier = Modifier.clickable(enabled = !isLoading) { onSkip() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DetailScreenPreview() {
    MaterialTheme {
        DetailScreen(
            onSkip = {},
            onBack = {},
            onContinue = { _, _, _, _ -> }
        )
    }
}