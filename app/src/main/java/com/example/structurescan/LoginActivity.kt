package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.structurescan.Utils.AdminAccessChecker
import com.example.structurescan.Utils.UserSuspensionChecker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirestoreInstance.db

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            LoginScreen(this, auth, db, googleSignInClient)
        }
    }
}

@Composable
fun LoginScreen(
    activity: ComponentActivity,
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    googleSignInClient: GoogleSignInClient
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // ✅ Loading states
    var isEmailLoginLoading by remember { mutableStateOf(false) }
    var isGoogleLoginLoading by remember { mutableStateOf(false) }

    // ✅ Failed login attempts counter
    var failedLoginAttempts by remember { mutableStateOf(0) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // ✅ Forgot Password Dialog after 3 failed attempts
    if (showForgotPasswordDialog) {
        Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
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
                    // Warning icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFFFF3CD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "!",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Forgotten Your Password?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "You've attempted to login 3 times unsuccessfully. Would you like to recover your password?",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Recover Password button
                    Button(
                        onClick = {
                            showForgotPasswordDialog = false
                            val intent = Intent(context, EmailForgotPassword::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Recover Password", color = Color.White, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Try Again button
                    OutlinedButton(
                        onClick = {
                            showForgotPasswordDialog = false
                            failedLoginAttempts = 0 // Reset counter
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF666666)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Try Again", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Google Sign-In launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            // ✅ Start Google loading
            isGoogleLoginLoading = true

            auth.signInWithCredential(credential)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                        // ✅ CHECK IF ADMIN FIRST!
                        AdminAccessChecker.checkIfAdmin(
                            context = context,
                            onNotAdmin = {
                                // ✅ Not admin - check suspension
                                UserSuspensionChecker.checkUserSuspension(
                                    context = context,
                                    onNotSuspended = {
                                        // User is NOT suspended - proceed
                                        db.collection("users").document(userId).get()
                                            .addOnSuccessListener { document ->
                                                isGoogleLoginLoading = false
                                                if (document.exists()) {
                                                    val role = document.getString("role") ?: "user"
                                                    val intent = Intent(activity, DashboardActivity::class.java)
                                                    activity.startActivity(intent)
                                                    activity.finish()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Account not registered. Please sign up first.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    auth.signOut()
                                                    googleSignInClient.signOut()
                                                }
                                            }
                                            .addOnFailureListener {
                                                isGoogleLoginLoading = false
                                                Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                                            }
                                    },
                                    onSuspended = {
                                        // User IS suspended - utility handles it
                                        isGoogleLoginLoading = false
                                        googleSignInClient.signOut()
                                    }
                                )
                            },
                            onAdmin = {
                                // 🚫 Admin detected - utility already showed toast and signed out
                                isGoogleLoginLoading = false
                                googleSignInClient.signOut()
                            }
                        )
                    } else {
                        isGoogleLoginLoading = false
                        Toast.makeText(context, "Google Sign-In failed!", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: ApiException) {
            isGoogleLoginLoading = false
            Toast.makeText(context, "Google sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = Color(0xFF0288D1).copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(75.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "StructureScan",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0288D1)
        )

        Text(
            text = "Structural safety in your hands",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Email Address Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Email Address",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Enter your email address", color = Color.Gray) },
                singleLine = true,
                enabled = !isEmailLoginLoading && !isGoogleLoginLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Password",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Enter your password", color = Color.Gray) },
                singleLine = true,
                enabled = !isEmailLoginLoading && !isGoogleLoginLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !isEmailLoginLoading && !isGoogleLoginLoading
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle Password",
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Forgot password?",
            color = if (isEmailLoginLoading || isGoogleLoginLoading) Color.Gray else Color(0xFF0288D1),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(enabled = !isEmailLoginLoading && !isGoogleLoginLoading) {
                    val intent = Intent(context, EmailForgotPassword::class.java)
                    context.startActivity(intent)
                }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ✅ Login Button with Loading Spinner and Failed Attempts Tracking
        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isEmailLoginLoading = true

                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // ✅ Reset failed attempts on successful login
                                failedLoginAttempts = 0

                                val userId = auth.currentUser?.uid
                                if (userId != null) {
                                    // ✅ CHECK IF ADMIN FIRST!
                                    AdminAccessChecker.checkIfAdmin(
                                        context = context,
                                        onNotAdmin = {
                                            // ✅ Not admin - check suspension
                                            UserSuspensionChecker.checkUserSuspension(
                                                context = context,
                                                onNotSuspended = {
                                                    // User is NOT suspended - proceed
                                                    db.collection("users").document(userId).get()
                                                        .addOnSuccessListener { document ->
                                                            isEmailLoginLoading = false
                                                            if (document.exists()) {
                                                                val role = document.getString("role") ?: "user"


                                                                val intent = Intent(activity, DashboardActivity::class.java)
                                                                activity.startActivity(intent)
                                                                activity.finish()
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Account not Registered.",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                        .addOnFailureListener {
                                                            isEmailLoginLoading = false
                                                            Toast.makeText(
                                                                context,
                                                                "Failed to fetch user data",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                },
                                                onSuspended = {
                                                    // User IS suspended - utility handles it
                                                    isEmailLoginLoading = false
                                                }
                                            )
                                        },
                                        onAdmin = {
                                            // 🚫 Admin detected - utility already showed toast and signed out
                                            isEmailLoginLoading = false
                                        }
                                    )
                                }
                            } else {
                                isEmailLoginLoading = false

                                // ✅ Increment failed attempts counter
                                failedLoginAttempts++

                                // ✅ Show forgot password dialog after 3 failed attempts
                                if (failedLoginAttempts >= 3) {
                                    showForgotPasswordDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Login failed: ${task.exception?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                } else {
                    Toast.makeText(
                        context,
                        "Email and Password cannot be empty.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            enabled = !isEmailLoginLoading && !isGoogleLoginLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0288D1),
                disabledContainerColor = Color(0xFF0288D1).copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            if (isEmailLoginLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("LOG IN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Or continue with",
            color = if (isEmailLoginLoading || isGoogleLoginLoading) Color.Gray.copy(alpha = 0.5f) else Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ✅ Google Sign In Button with Loading Spinner
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isGoogleLoginLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(50.dp),
                    color = Color(0xFF0288D1),
                    strokeWidth = 3.dp
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(50.dp)
                        .clickable(enabled = !isEmailLoginLoading) {
                            val signInIntent = googleSignInClient.signInIntent
                            launcher.launch(signInIntent)
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo),
                            contentDescription = "Google Sign In",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't have an account? ", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = "Create Account",
                color = if (isEmailLoginLoading || isGoogleLoginLoading) Color.Gray else Color(0xFF0288D1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !isEmailLoginLoading && !isGoogleLoginLoading) {
                    val intent = Intent(activity, RegisterActivity::class.java)
                    activity.startActivity(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    // Preview without Firebase initialization
    LoginScreenPreviewContent()
}

@Composable
private fun LoginScreenPreviewContent() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isEmailLoginLoading by remember { mutableStateOf(false) }
    var isGoogleLoginLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Logo placeholder
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = Color(0xFF0288D1).copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "LOGO",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0288D1)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "StructureScan",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0288D1)
        )

        Text(
            text = "Structural safety in your hands",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Email Address Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Email Address",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Enter your email address", color = Color.Gray) },
                singleLine = true,
                enabled = !isEmailLoginLoading && !isGoogleLoginLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0288D1),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Password",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Enter your password", color = Color.Gray) },
                singleLine = true,
                enabled = !isEmailLoginLoading && !isGoogleLoginLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !isEmailLoginLoading && !isGoogleLoginLoading
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle Password",
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Forgot password?",
            color = Color(0xFF0288D1),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.End)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Login Button
        Button(
            onClick = { },
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0288D1)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("LOG IN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Or continue with",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Google Sign In Button placeholder
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.size(50.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    "G",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't have an account? ", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = "Create Account",
                color = Color(0xFF0288D1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}