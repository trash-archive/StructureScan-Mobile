package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScanScreen(
                    onBackClick = {
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                    },
                    onContinue = { assessmentName ->
                        val intent = Intent(this, GuideActivity::class.java)
                        intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun ScanScreen(onBackClick: () -> Unit, onContinue: (String) -> Unit) {
    var assessmentName by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            BottomNavigationBarDashboardScan(currentRoute = "home")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // --- Top Bar with Centered Title + Back Arrow ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // Back Button (left aligned)
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onBackClick() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Centered Title
                    Text(
                        text = "Create New Assessment",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main title
            Text(
                text = "Name Your Assessment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Give this assessment a memorable name",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Icon (replace with logo if available)
            Icon(
                painter = painterResource(id = R.drawable.assessment),
                contentDescription = "Scan Icon",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF0288D1)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Input field
            OutlinedTextField(
                value = assessmentName,
                onValueChange = { assessmentName = it },
                label = { Text("Structure Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button with validation
            Button(
                onClick = {
                    if (assessmentName.trim().isEmpty()) {
                        Toast.makeText(
                            context,
                            "Please enter an assessment name",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onContinue(assessmentName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                Text("Continue", color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "This name cannot be changed. Please choose carefully.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BottomNavigationBarDashboardScan(currentRoute: String = "home") {
    val context = LocalContext.current
    NavigationBar(
        containerColor = Color.White
    ) {
        // Home
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { /* Already in Home */ },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "Home",
                    color = if (currentRoute == "home") Color(0xFF0288D1) else Color.Gray
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent, // removes oblong background
                selectedIconColor = Color(0xFF0288D1),
                unselectedIconColor = Color.Gray,
                selectedTextColor = Color(0xFF0288D1),
                unselectedTextColor = Color.Gray
            )
        )

        // History
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick = {
                val intent = Intent(context, HistoryActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History",
                    tint = if (currentRoute == "history") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "History",
                    color = if (currentRoute == "history") Color(0xFF0288D1) else Color.Gray
                )
            }
        )

        // Profile
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = {
                val intent = Intent(context, ProfileActivity::class.java)
                context.startActivity(intent)
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "profile") Color(0xFF0288D1) else Color.Gray
                )
            },
            label = {
                Text(
                    "Profile",
                    color = if (currentRoute == "profile") Color(0xFF0288D1) else Color.Gray
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ScanScreenPreview() {
    MaterialTheme {
        ScanScreen(onBackClick = {}, onContinue = {})
    }
}
