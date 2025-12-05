package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.structurescan.Utils.AdminAccessChecker
import com.example.structurescan.Utils.UserSuspensionChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class LoadingActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setContent {
            MaterialTheme {
                LoadingScreen(
                    onTimeout = {
                        checkAuthAndNavigate()
                    }
                )
            }
        }
    }

    private fun checkAuthAndNavigate() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // ✅ User is logged in - CHECK ADMIN FIRST!
            AdminAccessChecker.checkIfAdmin(
                context = this,
                onNotAdmin = {
                    // ✅ Not an admin - NOW check suspension
                    UserSuspensionChecker.checkUserSuspension(
                        context = this,
                        onNotSuspended = {
                            // ✅ Not admin AND not suspended - GO TO DASHBOARD!
                            val intent = Intent(this, DashboardActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        onSuspended = {
                            // 🚫 User IS suspended - already redirected to Login
                            finish()
                        }
                    )
                },
                onAdmin = {
                    // 🚫 User IS admin - already blocked with toast and redirected to Login
                    finish()
                }
            )
        } else {
            // ❌ No user logged in - go to Login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun LoadingScreen(onTimeout: () -> Unit) {
    // Fade in animation
    val alpha by rememberInfiniteTransition(label = "alpha").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Scale animation for logo
    val scale by rememberInfiniteTransition(label = "scale").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(3000) // 3 seconds splash
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1976D2),
                        Color(0xFF1565C0),
                        Color(0xFF0D47A1)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo with scale animation
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "StructureScan Logo",
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .padding(bottom = 32.dp)
            )

            // App Title
            Text(
                text = "StructureScan",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Subtitle
            Text(
                text = "Building Inspection System",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Progress Indicator
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 4.dp,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 16.dp)
            )

            // Loading Text with pulse animation
            Text(
                text = "Loading your workspace...",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(alpha)
            )
        }

        // Footer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Powered by StructureScan",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )

            Text(
                text = "Version 1.0",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingScreenPreview() {
    MaterialTheme {
        LoadingScreen(onTimeout = {})
    }
}