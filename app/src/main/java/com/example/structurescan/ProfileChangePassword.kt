package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class ProfileChangePassword : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ProfileChangePasswordScreen(
                    onBackClick = {
                        finish()
                    },
                    onPasswordChanged = {
                        // Sign out and redirect to login
                        val auth = FirebaseAuth.getInstance()
                        auth.signOut()

                        // Sign out from Google too
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                        val googleClient = GoogleSignIn.getClient(this, gso)
                        googleClient.signOut().addOnCompleteListener {
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileChangePasswordScreen(
    onBackClick: () -> Unit,
    onPasswordChanged: () -> Unit,
    firebaseUser: FirebaseUser? = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
) {
    val context = LocalContext.current
    val currentUser = firebaseUser

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Password validation
    fun validatePassword(password: String): String {
        return when {
            password.length < 8 -> "Password must be at least 8 characters"
            !password.any { it.isUpperCase() } -> "Must contain an uppercase letter"
            !password.any { it.isLowerCase() } -> "Must contain a lowercase letter"
            !password.any { it.isDigit() } -> "Must contain a number"
            else -> ""
        }
    }

    // ✅ Success Dialog - matches ProfileActivity logout dialog design
    if (showSuccessDialog) {
        Dialog(onDismissRequest = { /* prevent dismiss */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Password Changed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Your password has been changed successfully. Please login again with your new password.",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onPasswordChanged() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Login Again", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = "Change Password",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current Password Field with Label
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Current Password",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = currentPassword,
                onValueChange = {
                    currentPassword = it
                    errorMessage = ""
                },
                placeholder = { Text("Enter current password", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (currentPasswordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                        Icon(
                            if (currentPasswordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color.Gray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // New Password Field with Label
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "New Password",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    errorMessage = ""
                },
                placeholder = { Text("Enter new password", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (newPasswordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color.Gray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Password Field with Label
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Confirm New Password",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                placeholder = { Text("Confirm new password", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (confirmPasswordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color.Gray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Password Requirements
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Password Requirements:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• At least 8 characters\n" +
                            "• Include uppercase and lowercase letters\n" +
                            "• Include at least one number",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
        }

        // Error message
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Update Password Button
        Button(
            onClick = {
                when {
                    currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() -> {
                        errorMessage = "All fields must be filled in!"
                    }
                    newPassword != confirmPassword -> {
                        errorMessage = "New passwords do not match"
                    }
                    currentPassword == newPassword -> {
                        errorMessage = "New password must be different from current password"
                    }
                    else -> {
                        val validation = validatePassword(newPassword)
                        if (validation.isNotEmpty()) {
                            errorMessage = validation
                        } else {
                            // ✅ All validations passed - update password in Firebase
                            if (currentUser == null) {
                                errorMessage = "User not authenticated"
                                return@Button
                            }

                            isLoading = true
                            val email = currentUser.email ?: ""
                            val credential = EmailAuthProvider.getCredential(email, currentPassword)

                            // Re-authenticate user first
                            currentUser.reauthenticate(credential)
                                .addOnSuccessListener {
                                    // Now update password
                                    currentUser.updatePassword(newPassword)
                                        .addOnSuccessListener {
                                            isLoading = false
                                            showSuccessDialog = true
                                        }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            errorMessage = "Failed to update password: ${e.message}"
                                        }
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    errorMessage = when {
                                        e.message?.contains("password is invalid", ignoreCase = true) == true ->
                                            "Current password is incorrect"
                                        e.message?.contains("network", ignoreCase = true) == true ->
                                            "Network error. Please check your connection"
                                        else -> "Authentication failed. Please try again"
                                    }
                                }
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
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
                Text(
                    text = "Update Password",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileChangePasswordPreview() {
    MaterialTheme {
        ProfileChangePasswordScreen(
            onBackClick = {},
            onPasswordChanged = {},
            firebaseUser = null // Preview mode - no Firebase user
        )
    }
}