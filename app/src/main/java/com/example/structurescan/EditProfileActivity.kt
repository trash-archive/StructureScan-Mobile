package com.example.structurescan
import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

class EditProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EditProfileScreen(
                onBackClick = { finish() },
                onSaveChanges = { _, _, _, _ ->
                    finish()
                }
            )
        }
    }
}

// ✅ Helper function to get user initial from name or email
@Composable
fun getEditProfileInitial(name: String, email: String): String {
    return when {
        name.isNotBlank() && name != "Unknown User" -> {
            name.trim().firstOrNull()?.uppercase() ?: "U"
        }
        email.isNotBlank() && email != "No Email" -> {
            email.trim().firstOrNull()?.uppercase() ?: "U"
        }
        else -> "U"
    }
}

// ✅ Generate color based on the initial letter
@Composable
fun getEditProfileColorForInitial(initial: String): Color {
    val colors = listOf(
        Color(0xFF0288D1), // Blue
        Color(0xFF00796B), // Teal
        Color(0xFF5E35B1), // Purple
        Color(0xFFD32F2F), // Red
        Color(0xFFF57C00), // Orange
        Color(0xFF388E3C), // Green
        Color(0xFFC2185B), // Pink
        Color(0xFF7B1FA2), // Deep Purple
        Color(0xFF1976D2), // Blue
        Color(0xFF00897B)  // Teal
    )

    val index = (initial.firstOrNull()?.code ?: 0) % colors.size
    return colors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit,
    onSaveChanges: (String, String, String, Uri?) -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    // ✅ Only initialize Firebase if NOT in preview mode
    val auth = if (!isPreview) FirebaseAuth.getInstance() else null
    val firestore = if (!isPreview) FirebaseFirestore.getInstance() else null
    val storage = if (!isPreview) FirebaseStorage.getInstance() else null
    val currentUser = auth?.currentUser

    // UI states
    var fullName by remember { mutableStateOf("John Doe") }
    var email by remember { mutableStateOf("john.doe@example.com") }
    var originalEmail by remember { mutableStateOf("john.doe@example.com") }
    var selectedProfession by remember { mutableStateOf("Engineer") }
    var customProfession by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Local image states
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var fullNameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }

    // ✅ Loading state
    var isLoading by remember { mutableStateOf(false) }

    // ✅ Dialog states
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showEmailVerificationDialog by remember { mutableStateOf(false) }
    var newEmailAddress by remember { mutableStateOf("") }

    val isGoogleUser = currentUser?.providerData?.any { it.providerId == "google.com" } == true

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

    val coroutineScope = rememberCoroutineScope()

    // ✅ Get user initial and color for placeholder
    val userInitial = getEditProfileInitial(fullName, email)
    val initialColor = getEditProfileColorForInitial(userInitial)

    // ✅ Camera Launcher (defined FIRST)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imageBitmap = it
            imageUri = null
        }
    }

    // ✅ Camera Permission Launcher (uses cameraLauncher)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            showPermissionDeniedDialog = true
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            imageBitmap = null
        }
    }

    // Load user data once (only if not in preview)
    LaunchedEffect(currentUser?.uid) {
        if (isPreview) return@LaunchedEffect

        currentUser?.uid?.let { uid ->
            try {
                val doc = firestore!!.collection("users").document(uid).get().await()
                fullName = doc.getString("fullName") ?: currentUser.displayName ?: ""
                email = doc.getString("email") ?: currentUser.email ?: ""
                originalEmail = email

                // ✅ Load profession (try new field first, fallback to old "role" field)
                val professionValue = doc.getString("profession") ?: doc.getString("role") ?: "Engineer"

                // Check if it's a custom profession
                if (professionValue in professionOptions) {
                    selectedProfession = professionValue
                    showCustomInput = professionValue == "Other (Please specify)"
                } else {
                    // It's a custom profession
                    selectedProfession = "Other (Please specify)"
                    customProfession = professionValue
                    showCustomInput = true
                }

                photoUrl = doc.getString("photoUrl") ?: currentUser.photoUrl?.toString()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Validation helpers
    fun validateFullName(name: String): String {
        return when {
            name.isBlank() -> "Full name is required"
            name.length < 2 -> "Name must be at least 2 characters"
            !name.matches(Regex("^[a-zA-Z\\s]+$")) -> "Only letters and spaces allowed"
            else -> ""
        }
    }

    fun validateEmailAddress(e: String): String {
        val pattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return when {
            e.isBlank() -> "Email required"
            !pattern.matcher(e).matches() -> "Invalid email"
            else -> ""
        }
    }

    // ✅ Check if email already exists in Firestore
    suspend fun isEmailAlreadyUsed(emailToCheck: String, currentUid: String): Boolean {
        if (isPreview || firestore == null) return false
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", emailToCheck)
                .get()
                .await()

            // Email exists if we find any document that's not the current user
            querySnapshot.documents.any { it.id != currentUid }
        } catch (e: Exception) {
            false
        }
    }

    // Upload helpers
    suspend fun uploadBitmap(uid: String, bitmap: Bitmap): String {
        if (isPreview || storage == null) return ""
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val bytes = baos.toByteArray()
        val ref = storage.reference.child("profile_images/$uid.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadUri(uid: String, uri: Uri): String {
        if (isPreview || storage == null) return ""
        val ref = storage.reference.child("profile_images/$uid.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    val isFormValid = remember(fullNameError, emailError, fullName, email, selectedProfession, customProfession, showCustomInput) {
        val professionValid = if (showCustomInput) {
            customProfession.trim().isNotBlank()
        } else {
            selectedProfession.isNotBlank()
        }
        (fullNameError.isEmpty() && emailError.isEmpty() && fullName.isNotBlank() && email.isNotBlank() && professionValid)
    }

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

    // ✅ NEW: Email Verification Dialog
    if (showEmailVerificationDialog) {
        Dialog(onDismissRequest = { showEmailVerificationDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Verify Your Email",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "We've sent a verification link to:",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        newEmailAddress,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Please check your inbox and click the verification link to complete your email update. Your login email will change after verification.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showEmailVerificationDialog = false
                            onBackClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Got It", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // optional: adjust height
                color = Color.White,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    // Back Button (Left aligned)
                    IconButton(
                        onClick = { onBackClick() },
                        enabled = !isLoading,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Centered Title
                    Text(
                        text = "Edit Profile",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1)
                    )
                }
            }

            // Profile picture with Initial Placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (imageBitmap == null && imageUri == null && photoUrl.isNullOrEmpty()) {
                            initialColor
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(enabled = !isLoading) {
                        showImageSourceDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap!!.asImageBitmap(),
                            contentDescription = "Selected photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    imageUri != null -> {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageUri),
                            contentDescription = "Selected photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    !photoUrl.isNullOrEmpty() -> {
                        Image(
                            painter = rememberAsyncImagePainter(model = photoUrl),
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Text(
                            text = userInitial,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = "Change Photo",
                color = if (isLoading) Color.Gray else Color(0xFF0288D1),
                modifier = Modifier.clickable(enabled = !isLoading) {
                    showImageSourceDialog = true
                }
            )

            // ✅ Image source dialog with permission handling
            if (showImageSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showImageSourceDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showImageSourceDialog = false
                            if (!isPreview) galleryLauncher.launch("image/*")
                        }) {
                            Text("Choose from Gallery")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showImageSourceDialog = false
                            if (!isPreview) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text("Take Photo")
                        }
                    },
                    title = { Text("Select Image") },
                    text = { Text("Choose an image from Gallery or take a photo with Camera.") }
                )
            }

            // Full name
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    fullNameError = validateFullName(it)
                },
                label = { Text("Full Name") },
                readOnly = isGoogleUser,
                enabled = !isLoading,
                isError = fullNameError.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            if (fullNameError.isNotEmpty()) {
                Text(fullNameError, color = Color.Red, fontSize = 12.sp)
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = validateEmailAddress(it)
                },
                label = { Text("Email Address") },
                readOnly = isGoogleUser,
                enabled = !isLoading,
                isError = emailError.isNotEmpty(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            if (emailError.isNotEmpty()) {
                Text(emailError, color = Color.Red, fontSize = 12.sp)
            }

            // ✅ Profession dropdown with "Other" option
            Column(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { if (!isLoading) isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProfession,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoading,
                        label = { Text("Profession") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
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
                                    showCustomInput = profession == "Other (Please specify)"
                                    if (!showCustomInput) {
                                        customProfession = ""
                                    }
                                }
                            )
                        }
                    }
                }

                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customProfession,
                        onValueChange = { customProfession = it },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Specify Your Profession") },
                        placeholder = { Text("e.g. Civil Engineer, Surveyor", color = Color.Gray) },
                        singleLine = true
                    )
                }
            }

            // ✅ Save button (disabled in preview)
            Button(
                onClick = {
                    if (isPreview) return@Button

                    fullNameError = validateFullName(fullName)
                    emailError = validateEmailAddress(email)

                    if (selectedProfession == "Other (Please specify)" && customProfession.trim().isEmpty()) {
                        Toast.makeText(context, "Please specify your profession", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (!isFormValid) return@Button

                    val uid = currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(context, "No user signed in", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    coroutineScope.launch {
                        try {
                            var emailChanged = false

                            // ✅ Step 1: Check for duplicate email & Send verification email
                            if (!isGoogleUser && email.lowercase().trim() != originalEmail.lowercase().trim()) {
                                // Check if email exists
                                val emailExists = isEmailAlreadyUsed(email.trim(), uid)
                                if (emailExists) {
                                    isLoading = false
                                    emailError = "This email is already in use"
                                    Toast.makeText(
                                        context,
                                        "This email is already registered. Please use a different email.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }

                                // ✅ Send verification email to new address
                                try {
                                    currentUser.verifyBeforeUpdateEmail(email.trim()).await()
                                    emailChanged = true
                                } catch (authException: Exception) {
                                    isLoading = false

                                    val errorMessage = when {
                                        authException.message?.contains("requires-recent-login", ignoreCase = true) == true ->
                                            "For security, please log out and log back in before changing your email address."
                                        authException.message?.contains("invalid-email", ignoreCase = true) == true ->
                                            "Invalid email format. Please check and try again."
                                        authException.message?.contains("email-already-in-use", ignoreCase = true) == true ->
                                            "This email is already registered to another account."
                                        else -> "Failed to send verification email. Please try logging out and back in."
                                    }

                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                            }

                            // ✅ Step 2: Upload photo if needed
                            val newPhotoUrl = when {
                                imageBitmap != null -> uploadBitmap(uid, imageBitmap!!)
                                imageUri != null -> uploadUri(uid, imageUri!!)
                                else -> photoUrl
                            }

                            // ✅ Step 3: Update Firestore
                            val finalProfession = if (selectedProfession == "Other (Please specify)") {
                                customProfession.trim()
                            } else {
                                selectedProfession
                            }

                            val updateMap = mutableMapOf<String, Any>(
                                "profession" to finalProfession,
                                "photoUrl" to (newPhotoUrl ?: "")
                            )
                            if (!isGoogleUser) {
                                updateMap["fullName"] = fullName
                                updateMap["email"] = email.trim()
                            }

                            firestore!!.collection("users")
                                .document(uid)
                                .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                                .await()

                            photoUrl = newPhotoUrl
                            imageBitmap = null
                            imageUri = null
                            originalEmail = email

                            isLoading = false

                            // ✅ Show appropriate success message
                            if (emailChanged) {
                                newEmailAddress = email.trim()
                                showEmailVerificationDialog = true
                            } else {
                                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                onSaveChanges(fullName, email, finalProfession, null)
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0288D1),
                    disabledContainerColor = Color(0xFF0288D1).copy(alpha = 0.6f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Cancel",
                color = if (isLoading) Color.Gray else Color(0xFF0288D1),
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onBackClick() }
                    .padding(8.dp)
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun EditProfileScreenPreview() {
    EditProfileScreen(onBackClick = {}, onSaveChanges = { _, _, _, _ -> })
}