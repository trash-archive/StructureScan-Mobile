package com.example.structurescan

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoadingActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setContent {
            MaterialTheme {
                LoadingScreen(
                    onCheckAuth = { checkAuthAndNavigate() },
                    isNetworkAvailable = { isNetworkAvailable() }
                )
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun checkAuthAndNavigate(): Boolean {
        return try {
            val currentUser = auth.currentUser

            if (currentUser != null) {
                currentUser.getIdToken(true).await()

                var isAdmin = false
                var isSuspended = false

                val adminDoc = db.collection("admins").document(currentUser.uid).get().await()
                isAdmin = adminDoc.exists()

                if (isAdmin) {
                    runOnUiThread {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    return true
                }

                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                isSuspended = userDoc.getBoolean("suspended") ?: false

                if (isSuspended) {
                    runOnUiThread {
                        auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    return true
                }

                runOnUiThread {
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                true
            } else {
                runOnUiThread {
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

@Composable
fun LoadingScreen(
    onCheckAuth: suspend () -> Boolean,
    isNetworkAvailable: () -> Boolean
) {
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun attemptLoad() {
        showError = false
        isLoading = true

        delay(500)

        if (!isNetworkAvailable()) {
            errorMessage = "No internet connection"
            showError = true
            isLoading = false
            return
        }

        val success = onCheckAuth()

        if (!success) {
            errorMessage = "Connection failed"
            showError = true
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        attemptLoad()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2563EB),
                        Color(0xFF1D4ED8),
                        Color(0xFF1E40AF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ✅ Show loading or error
        AnimatedVisibility(
            visible = !showError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingContent()
        }

        AnimatedVisibility(
            visible = showError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ErrorContent(
                errorMessage = errorMessage,
                isLoading = isLoading,
                onRetry = {
                    coroutineScope.launch {
                        attemptLoad()
                    }
                }
            )
        }
    }
}

@Composable
fun LoadingContent() {
    val scale by rememberInfiniteTransition(label = "scale").animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by rememberInfiniteTransition(label = "alpha").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "StructureScan Logo",
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "StructureScan",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Building Inspection System",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp
        )

        Spacer(Modifier.height(48.dp))

        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier
                .size(40.dp)
                .alpha(alpha)
        )
    }
}

// ✅ MINIMAL ERROR SCREEN - Clean & Simple
@Composable
fun ErrorContent(
    errorMessage: String,
    isLoading: Boolean,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        // Logo (same as loading)
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "StructureScan Logo",
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "StructureScan",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(Modifier.height(40.dp))

        // ✅ Simple error message
        Text(
            text = errorMessage,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // ✅ Minimal retry button
        Button(
            onClick = onRetry,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF2563EB)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF2563EB),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Retry",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
