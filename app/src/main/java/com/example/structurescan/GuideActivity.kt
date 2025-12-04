package com.example.structurescan
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class GuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assessmentName = intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"
        setContent {
            MaterialTheme {
                GuideScreen(
                    onBackClick = {
                        val intent = Intent(this, ScanActivity::class.java)
                        startActivity(intent)
                    },
                    onScanNow = {
                        val intent = Intent(this, CaptureImagesActivity::class.java)
                        intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun GuideScreen(onBackClick: () -> Unit, onScanNow: () -> Unit) {
    val scrollState = rememberScrollState()
    val proTips = listOf(
        "Clear, well-lit photos from multiple angles help the AI provide you with the most accurate safety assessment results.",
        "Taking photos in natural daylight produces the best results. Avoid using flash as it can create unwanted reflections.",
        "Capture at least 5-8 photos from different perspectives to ensure comprehensive coverage of the area."
    )
    var currentTip by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTip = (currentTip + 1) % proTips.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF2563EB))
            }
            Text(
                "Create New Assessment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2563EB),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Photo Guide section
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB))),
                        CircleShape
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Text("Photo Guide", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.Black,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Follow these guidelines to capture high-quality photos for accurate assessment results.",
            fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Internet Connection warning
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE0F2FF), RoundedCornerShape(8.dp))
                .border(1.5.dp, Color(0xFF2563EB), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.Wifi, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Stable internet connection required to upload and process your photos.",
                color = Color(0xFF2563EB), fontSize = 14.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Do's Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FADF)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(40.dp).background(Color(0xFF34D399), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Do's", color = Color(0xFF097B53), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                DoDontRow(Icons.Outlined.Search, Color(0xFF34D399), "Good lighting")
                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Stay steady")
                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Wide shots")
                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Multiple angles")
                DoDontRow(Icons.Outlined.Search, Color(0xFF34D399), "Zoom on details")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Don'ts Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(40.dp).background(Color(0xFFF87171), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Don'ts", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Blurry photos")
                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Too close")
                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Cropped parts")
                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Obstructions")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick Steps section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(Color(0xFF2563EB), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("1-4", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("Quick Steps", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE6ECFE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepBox(Modifier.weight(1f), 1, "Wide shot of building")
                    StepBox(Modifier.weight(1f), 2, "Different angles")
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepBox(Modifier.weight(1f), 3, "Close-ups of damage")
                    StepBox(Modifier.weight(1f), 4, "Review clarity")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Pro Tip Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFFB300), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = Color(0xFF6D4C00), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pro Tip", color = Color(0xFF995000), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        // Tip dots
                        Row {
                            proTips.forEachIndexed { idx, _ ->
                                Box(
                                    Modifier.height(8.dp)
                                        .width(if (idx == currentTip) 18.dp else 8.dp)
                                        .background(if (idx == currentTip) Color(0xFFF9A826) else Color(0xFFFDE68A),
                                            shape = RoundedCornerShape(5.dp))
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                        }
                    }
                    Crossfade(targetState = currentTip) { tipIndex ->
                        Text(proTips[tipIndex], fontSize = 14.sp, color = Color(0xFF996622),
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onScanNow,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Camera, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Start Taking Photos", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// Helper for Do's/Don'ts row
@Composable
fun DoDontRow(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 14.sp, color = Color.DarkGray)
    }
}

@Composable
fun StepBox(modifier: Modifier = Modifier, num: Int, label: String) {
    Card(
        modifier = modifier,   // Use modifier, not hardcoded .weight(1f)
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(24.dp).background(Color(0xFF2563EB), CircleShape), contentAlignment = Alignment.Center) {
                Text("$num", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuideScreenPreview() {
    GuideScreen({}, {})
}
