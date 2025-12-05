package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

// 🔹 Profile Activity - Displays user info + photo from Firebase
class ProfileActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    val nameFromDoc = when {
                        document.getString("fullName") != null -> document.getString("fullName")
                        document.getString("firstName") != null && document.getString("lastName") != null ->
                            document.getString("firstName") + " " + document.getString("lastName")
                        document.getString("username") != null -> document.getString("username")
                        currentUser.displayName != null -> currentUser.displayName
                        else -> "Unknown User"
                    }
                    val email = document.getString("email") ?: currentUser.email ?: "No Email"
                    val photoUrl = document.getString("photoUrl")

                    val isGoogleUser = currentUser.providerData.any {
                        it.providerId == GoogleAuthProvider.PROVIDER_ID
                    }

                    setContent {
                        MaterialTheme {
                            ProfileScreen(
                                name = nameFromDoc ?: "Unknown User",
                                email = email,
                                profileImageUrl = photoUrl,
                                isGoogleUser = isGoogleUser,
                                logoutButton = {
                                    auth.signOut()
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                    val googleClient = GoogleSignIn.getClient(this, gso)
                                    googleClient.signOut().addOnCompleteListener {
                                        val intent = Intent(this, LoginActivity::class.java)
                                        intent.flags =
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                                },
                                editClick = {
                                    startActivity(Intent(this, EditProfileActivity::class.java))
                                }
                            )
                        }
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
        }
    }
}

// ✅ Get user initial from name or email
fun getUserInitial(name: String, email: String): String {
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
fun getColorForInitial(initial: String): Color {
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

// 🔹 Profile Screen Composable
@Composable
fun ProfileScreen(
    name: String,
    email: String,
    profileImageUrl: String?,
    logoutButton: () -> Unit,
    editClick: () -> Unit,
    isGoogleUser: Boolean = false
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    // ✅ Get user initial for placeholder
    val userInitial = getUserInitial(name, email)
    val initialColor = getColorForInitial(userInitial)

    Scaffold(
        bottomBar = { BottomNavigationBar(currentScreen = "profile") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Back Button - Left aligned
                IconButton(
                    onClick = {
                        val intent = Intent(context, DashboardActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                // Centered Title
                Text(
                    text = "My Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0288D1),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ✅ Profile image with Initial Placeholder
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        if (profileImageUrl.isNullOrEmpty()) initialColor else Color.Transparent
                    )
                    .border(2.dp, Color(0xFF0288D1), CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (!profileImageUrl.isNullOrEmpty()) {
                    // ✅ Display user's uploaded profile picture
                    Image(
                        painter = rememberAsyncImagePainter(profileImageUrl),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // ✅ Display initial letter with white color
                    Text(
                        text = userInitial,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = email,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ✅ Edit Profile - Clickable with arrow
                ProfileOption(
                    title = "Edit Profile",
                    onClick = { editClick() },
                    showArrow = true
                )

                // ✅ Change Password - Clickable with arrow
                ProfileOption(
                    title = "Change Password",
                    onClick = {
                        if (isGoogleUser) {
                            Toast.makeText(
                                context,
                                "You're signed in with Google. Please update your password in your Gmail account settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val intent = Intent(context, ProfileChangePassword::class.java)
                            context.startActivity(intent)
                        }
                    },
                    showArrow = true
                )

                // ✅ About App - NOT clickable, no arrow, just displays version
                ProfileOption(
                    title = "About App",
                    trailingText = "1.0.1",
                    onClick = null,
                    showArrow = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // same height as profile options
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(10.dp), // same as ProfileOption
            ) {
                Text(
                    text = "Logout",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }

    if (showLogoutDialog) {
        LogoutDialog(
            onConfirmLogout = {
                showLogoutDialog = false
                logoutButton()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

// 🔹 Reusable Profile Option Row
@Composable
fun ProfileOption(
    title: String,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color.Black
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            if (showArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Arrow",
                    tint = Color.Gray
                )
            }
        }
    }
}

// 🔹 Logout Confirmation Dialog
@Composable
fun LogoutDialog(
    onConfirmLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFFEBEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Logout", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Are you sure you want to logout?",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF666666)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Cancel") }

                    Button(
                        onClick = onConfirmLogout,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Logout", color = Color.White) }
                }
            }
        }
    }
}

// 🔹 Bottom Navigation with updated colors
@Composable
fun BottomNavigationBar(currentScreen: String) {
    val context = LocalContext.current
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = currentScreen == "home",
            onClick = {
                val intent = Intent(context, DashboardActivity::class.java)
                context.startActivity(intent)
            },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentScreen == "history",
            onClick = {
                val intent = Intent(context, HistoryActivity::class.java)
                context.startActivity(intent)
            },
            icon = { Icon(Icons.Filled.History, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentScreen == "profile",
            onClick = { /* already here */ },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )
    }
}

// 🔹 Preview
@Preview(showBackground = true)
@Composable
fun ProfilePreview() {
    MaterialTheme {
        ProfileScreen(
            name = "Preview User",
            email = "preview@example.com",
            profileImageUrl = null,
            isGoogleUser = false,
            logoutButton = {},
            editClick = {}
        )
    }
}