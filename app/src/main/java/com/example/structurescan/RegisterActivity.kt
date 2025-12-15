package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirestoreInstance.db

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            RegisterScreen(this, auth, db, googleSignInClient)
        }
    }
}

// ✅ Password Validation Data Class
data class PasswordValidation(
    val hasMinLength: Boolean = false,
    val hasUpperCase: Boolean = false,
    val hasLowerCase: Boolean = false,
    val hasDigit: Boolean = false,
    val hasSpecialChar: Boolean = false
) {
    fun isValid(): Boolean = hasMinLength && hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar
}

// ✅ Email Validation Data Class
data class EmailValidation(
    val isValidFormat: Boolean = false,
    val hasAtSymbol: Boolean = false,
    val hasDomain: Boolean = false,
    val isNotEmpty: Boolean = false
) {
    fun isValid(): Boolean = isValidFormat && hasAtSymbol && hasDomain && isNotEmpty
}

// ✅ Password Validator Function
fun validatePassword(password: String): PasswordValidation {
    return PasswordValidation(
        hasMinLength = password.length >= 8,
        hasUpperCase = password.any { it.isUpperCase() },
        hasLowerCase = password.any { it.isLowerCase() },
        hasDigit = password.any { it.isDigit() },
        hasSpecialChar = password.any { !it.isLetterOrDigit() }
    )
}

// ✅ Email Validator Function
fun validateEmail(email: String): EmailValidation {
    val hasAtSymbol = email.contains("@")
    val hasDomain = email.contains("@") && email.substringAfter("@").contains(".")
    val isValidFormat = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    return EmailValidation(
        isValidFormat = isValidFormat,
        hasAtSymbol = hasAtSymbol,
        hasDomain = hasDomain,
        isNotEmpty = email.isNotEmpty()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    activity: ComponentActivity?,
    auth: FirebaseAuth?,
    db: FirebaseFirestore?,
    googleSignInClient: GoogleSignInClient?
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreeTerms by remember { mutableStateOf(false) }

    // ✅ Password validation state
    var passwordValidation by remember { mutableStateOf(PasswordValidation()) }
    var showPasswordRequirements by remember { mutableStateOf(false) }

    // ✅ Email validation state
    var emailValidation by remember { mutableStateOf(EmailValidation()) }
    var showEmailRequirements by remember { mutableStateOf(false) }
    var emailAlreadyExists by remember { mutableStateOf(false) }
    var isCheckingEmail by remember { mutableStateOf(false) }

    // ✅ Terms dialog states
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // ✅ Loading states
    var isEmailSignUpLoading by remember { mutableStateOf(false) }
    var isGoogleSignUpLoading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (activity == null || auth == null || db == null) return@rememberLauncherForActivityResult

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            isGoogleSignUpLoading = true

            auth.signInWithCredential(credential)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        val user = auth.currentUser
                        val userId = user?.uid
                        val userData = hashMapOf(
                            "fullName" to (user?.displayName ?: ""),
                            "email" to (user?.email ?: ""),
                            "profession" to "",
                            "photoUrl" to (user?.photoUrl?.toString() ?: "")
                        )

                        if (userId != null) {
                            db.collection("users").document(userId)
                                .set(userData)
                                .addOnSuccessListener {
                                    isGoogleSignUpLoading = false
                                    Toast.makeText(activity, "Google Sign-Up Successful!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(activity, DetailActivity::class.java)
                                    activity.startActivity(intent)
                                    activity.finish()
                                }
                                .addOnFailureListener {
                                    isGoogleSignUpLoading = false
                                    Toast.makeText(activity, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        isGoogleSignUpLoading = false
                        Toast.makeText(activity, "Google Sign-Up Failed.", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: ApiException) {
            isGoogleSignUpLoading = false
            Toast.makeText(activity, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Terms and Conditions Dialog
    if (showTermsDialog) {
        TermsDialog(
            onDismiss = { showTermsDialog = false },
            title = "Terms and Conditions"
        )
    }

    // ✅ Privacy Policy Dialog
    if (showPrivacyDialog) {
        PrivacyDialog(
            onDismiss = { showPrivacyDialog = false },
            title = "Privacy Policy"
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ✅ Header with Back Button
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
                // Back button (left aligned)
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (activity != null) {
                                val intent = Intent(activity, LoginActivity::class.java)
                                activity.startActivity(intent)
                                activity.finish()
                            }
                        },
                        enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isEmailSignUpLoading || isGoogleSignUpLoading) Color.Gray else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Centered Title
                Text(
                    text = "Create New Account",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1)
                )
            }
        }

        // ✅ Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ✅ Full Name Field with Label
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Full Name",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = { Text("John Doe", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0288D1),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Email Field with Label
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Email Address",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailValidation = validateEmail(it)
                        showEmailRequirements = it.isNotEmpty()
                        emailAlreadyExists = false // Reset when user types
                    },
                    placeholder = { Text("example@gmail.com", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading,
                    isError = (showEmailRequirements && !emailValidation.isValid()) || emailAlreadyExists,
                    trailingIcon = {
                        if (isCheckingEmail) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF0288D1),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0288D1),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        errorBorderColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // ✅ Email Requirements Indicator
            if (showEmailRequirements && !emailValidation.isValid()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Valid email must have:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        EmailRequirementItem("Not empty", emailValidation.isNotEmpty)
                        EmailRequirementItem("Contains @ symbol", emailValidation.hasAtSymbol)
                        EmailRequirementItem("Valid domain (e.g., gmail.com)", emailValidation.hasDomain)
                    }
                }
            }

            // ✅ Email Already Exists Message
            if (emailAlreadyExists) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Email already registered",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                            Text(
                                text = "This email is already in use. Please use a different email or try logging in.",
                                fontSize = 11.sp,
                                color = Color(0xFF666666),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Password Field with Label
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Password",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordValidation = validatePassword(it)
                        showPasswordRequirements = it.isNotEmpty()
                    },
                    placeholder = { Text("Enter your password", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading
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

            // ✅ Password Requirements Indicator
            if (showPasswordRequirements) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Password must contain:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        PasswordRequirementItem("At least 8 characters", passwordValidation.hasMinLength)
                        PasswordRequirementItem("One uppercase letter (A-Z)", passwordValidation.hasUpperCase)
                        PasswordRequirementItem("One lowercase letter (a-z)", passwordValidation.hasLowerCase)
                        PasswordRequirementItem("One number (0-9)", passwordValidation.hasDigit)
                        PasswordRequirementItem("One special character (!@#$%^&*)", passwordValidation.hasSpecialChar)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Confirm Password Field with Label
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Confirm Password",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirm your password", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                            enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading
                        ) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = Color.Gray
                            )
                        }
                    },
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                            Text(
                                text = "Passwords do not match",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
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

            // ✅ Terms Checkbox with Clickable Links
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = agreeTerms,
                    onCheckedChange = { agreeTerms = it },
                    enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading
                )
                Text(
                    text = buildAnnotatedString {
                        append("I agree to ")
                        withStyle(style = SpanStyle(color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)) {
                            append("Terms and Conditions")
                        }
                        append(" and have read the ")
                        withStyle(style = SpanStyle(color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)) {
                            append("Privacy Policy")
                        }
                    },
                    fontSize = 14.sp,
                    color = if (isEmailSignUpLoading || isGoogleSignUpLoading) Color.Gray else Color.Black,
                    modifier = Modifier.clickable(enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading) {
                        showTermsDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ✅ Sign Up Button
            Button(
                onClick = {
                    if (activity == null || auth == null || db == null) return@Button

                    if (fullName.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        Toast.makeText(activity, "All fields are required.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (!emailValidation.isValid()) {
                        Toast.makeText(activity, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (!passwordValidation.isValid()) {
                        Toast.makeText(activity, "Password does not meet the requirements.", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    if (password != confirmPassword) {
                        Toast.makeText(activity, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (!agreeTerms) {
                        Toast.makeText(activity, "You must agree to the terms.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // ✅ Check if email already exists
                    isCheckingEmail = true
                    auth.fetchSignInMethodsForEmail(email)
                        .addOnCompleteListener { task ->
                            isCheckingEmail = false
                            if (task.isSuccessful) {
                                val signInMethods = task.result?.signInMethods
                                if (!signInMethods.isNullOrEmpty()) {
                                    // Email already exists
                                    emailAlreadyExists = true
                                } else {
                                    // Email is available, proceed with registration
                                    isEmailSignUpLoading = true

                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener { createTask ->
                                            if (createTask.isSuccessful) {
                                                val user = auth.currentUser
                                                val userId = user?.uid

                                                val profileUpdates = UserProfileChangeRequest.Builder()
                                                    .setDisplayName(fullName)
                                                    .build()
                                                user?.updateProfile(profileUpdates)

                                                val userData = hashMapOf(
                                                    "fullName" to fullName,
                                                    "email" to email,
                                                    "profession" to "",
                                                    "photoUrl" to ""
                                                )

                                                if (userId != null) {
                                                    db.collection("users").document(userId)
                                                        .set(userData)
                                                        .addOnSuccessListener {
                                                            isEmailSignUpLoading = false
                                                            val intent = Intent(activity, DetailActivity::class.java)
                                                            activity.startActivity(intent)
                                                            activity.finish()
                                                        }
                                                        .addOnFailureListener {
                                                            isEmailSignUpLoading = false
                                                            Toast.makeText(activity, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                            } else {
                                                isEmailSignUpLoading = false
                                                Toast.makeText(activity, "Sign up failed: ${createTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                }
                            } else {
                                Toast.makeText(activity, "Error checking email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                },
                enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading && !isCheckingEmail,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0288D1),
                    disabledContainerColor = Color(0xFF0288D1).copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isEmailSignUpLoading || isCheckingEmail) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("SIGN UP", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Or continue with",
                color = if (isEmailSignUpLoading || isGoogleSignUpLoading) Color.Gray.copy(alpha = 0.5f) else Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign-In Button
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGoogleSignUpLoading) {
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
                            .clickable(enabled = !isEmailSignUpLoading && googleSignInClient != null) {
                                if (googleSignInClient != null) {
                                    val signInIntent = googleSignInClient.signInIntent
                                    launcher.launch(signInIntent)
                                }
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

            Row {
                Text("Already have an account? ", fontSize = 14.sp, color = Color.Gray)
                Text(
                    text = "Back to Log In",
                    color = if (isEmailSignUpLoading || isGoogleSignUpLoading) Color.Gray else Color(0xFF0288D1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isEmailSignUpLoading && !isGoogleSignUpLoading && activity != null) {
                        if (activity != null) {
                            val intent = Intent(activity, LoginActivity::class.java)
                            activity.startActivity(intent)
                            activity.finish()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ✅ Terms and Conditions Dialog
@Composable
fun TermsDialog(onDismiss: () -> Unit, title: String) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1),
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
                        Last Updated: November 13, 2025

                        1. Acceptance of Terms
                        By creating an account and using StructureScan, you agree to be bound by these Terms and Conditions.

                        2. User Accounts
                        • You must provide accurate and complete information during registration
                        • You are responsible for maintaining the confidentiality of your account credentials

                        3. User Conduct
                        You agree not to:
                        • Upload false, inaccurate, or misleading information
                        • Use the service for any illegal purposes
                        • Attempt to gain unauthorized access to our systems

                        4. Data and Privacy
                        • We collect and process your personal data in accordance with our Privacy Policy
                        • Profile photos and inspection data are stored securely on our servers
                        • You retain ownership of your uploaded content

                        5. Service Availability
                        • We strive to provide 24/7 service availability
                        • We do not guarantee uninterrupted access
                        • Scheduled maintenance may occur with prior notice

                        6. Intellectual Property
                        • The StructureScan app and all related content are protected by copyright
                        • You may not copy, modify, or distribute our software

                        7. Limitation of Liability
                        StructureScan is provided "as is" without warranties. We are not liable for any damages arising from the use of our service.

                        8. Changes to Terms
                        We reserve the right to modify these terms at any time. Continued use of the service constitutes acceptance of modified terms.

                        9. Termination
                        We reserve the right to suspend or terminate accounts that violate these terms.

                        10. Contact
                        For questions about these terms, please contact our support team through our email at structurescanapp@gmail.com.
                        """.trimIndent(),
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        lineHeight = 20.sp
                    )
                }

                HorizontalDivider()

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// ✅ Privacy Policy Dialog
@Composable
fun PrivacyDialog(onDismiss: () -> Unit, title: String) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1),
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
                        Last Updated: November 13, 2025

                        1. Information We Collect
                        We collect the following information:
                        • Full name and email address
                        • Professional information (Engineer, Architect, etc.)
                        • Profile photos (optional)
                        • Structural inspection photos and data
                        • Device information and usage data

                        2. How We Use Your Information
                        Your data is used to:
                        • Provide and maintain our services
                        • Authenticate your account
                        • Store and organize your inspection data
                        • Improve app functionality and user experience

                        3. Data Storage and Security
                        • All data is encrypted and stored on our servers
                        • We use industry-standard security measures
                        • Profile images are stored in Firebase Cloud Storage
                        • User credentials are handled by Firebase Authentication

                        4. Data Sharing
                        We do NOT:
                        • Sell your personal information to third parties
                        • Share your data for advertising purposes
                        • Disclose your information without consent

                        We MAY share data:
                        • When required by law
                        • To protect our legal rights
                        • With your explicit consent

                        5. Your Rights
                        You have the right to:
                        • Access your personal data
                        • Update or correct your information

                        6. Cookies and Tracking
                        • We use Firebase Analytics to understand app usage
                        • No third-party advertising cookies are used
                        • Session data helps maintain login status

                        7. Third-Party Services
                        We use:
                        • Firebase (Google) for authentication and data storage
                        • Google Sign-In for authentication

                        9. Changes to Privacy Policy
                        We will notify users of any material changes to this policy.

                        10. Contact Us
                        For privacy concerns, contact us through our email structurescanapp@gmail.com.
                        """.trimIndent(),
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        lineHeight = 20.sp
                    )
                }

                HorizontalDivider()

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// ✅ Password Requirement Item Composable
@Composable
fun PasswordRequirementItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (isMet) Color(0xFF4CAF50) else Color(0xFFE57373),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (isMet) Color(0xFF4CAF50) else Color(0xFF666666)
        )
    }
}

// ✅ Email Requirement Item Composable
@Composable
fun EmailRequirementItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (isMet) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (isMet) Color(0xFF4CAF50) else Color(0xFF666666)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    MaterialTheme {
        RegisterScreen(
            activity = null,
            auth = null,
            db = null,
            googleSignInClient = null
        )
    }
}