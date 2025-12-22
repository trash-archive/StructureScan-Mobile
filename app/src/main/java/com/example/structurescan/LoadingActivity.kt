package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
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
                    onCheckAuth = { checkAuthAndNavigate() }
                )
            }
        }
    }

    private suspend fun checkAuthAndNavigate() {
        try {
            val currentUser = auth.currentUser

            if (currentUser != null) {
                // Try to refresh token, but don't block if it fails
                try {
                    currentUser.getIdToken(true).await()
                } catch (e: Exception) {
                    // Ignore token refresh errors - allow offline access
                }

                // Try to check admin status, but don't block if it fails
                var isAdmin = false
                try {
                    val adminDoc = db.collection("admins").document(currentUser.uid).get().await()
                    isAdmin = adminDoc.exists()
                } catch (e: Exception) {
                    // Ignore admin check errors
                }

                if (isAdmin) {
                    runOnUiThread {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    return
                }

                // Try to check suspension status, but don't block if it fails
                try {
                    val userDoc = db.collection("users").document(currentUser.uid).get().await()
                    val isSuspended = userDoc.getBoolean("suspended") ?: false

                    if (isSuspended) {
                        runOnUiThread {
                            auth.signOut()
                            val intent = Intent(this, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        return
                    }
                } catch (e: Exception) {
                    // Ignore suspension check errors - allow offline access
                }

                // Navigate to dashboard (works offline)
                runOnUiThread {
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            } else {
                // No user logged in - go to login
                runOnUiThread {
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        } catch (e: Exception) {
            // On any error, try to navigate based on auth state
            runOnUiThread {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    // User exists, go to dashboard
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // No user, go to login
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(
    onCheckAuth: suspend () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1500)
        onCheckAuth()
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
        LoadingContent()
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