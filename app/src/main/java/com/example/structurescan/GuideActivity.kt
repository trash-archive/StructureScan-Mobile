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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
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
        "Capture 3-7 photos from different angles to ensure the AI can detect cracks, spalling, algae, and paint damage more accurately.",
        "Natural daylight provides the best results. Avoid flash photography as it can create reflections that may affect AI analysis.",
        "Take wide shots of the entire structure and close-ups of visible damage to help the AI assess both overall and detailed conditions.",
        "Ensure photos are clear and in focus. Blurry images reduce the AI's ability to detect structural issues like fine cracks."
    )
    var currentTip by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            currentTip = (currentTip + 1) % proTips.size
        }
    }

    Scaffold(
        bottomBar = {
            // Fixed Bottom Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Button(
                    onClick = onScanNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Camera, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Start Taking Photos", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
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

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(paddingValues)
            ) {
                Spacer(Modifier.height(20.dp))

                // Photo Guide section with improved spacing
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

                Spacer(Modifier.height(12.dp))

                Text(
                    "Photo Capture Guide",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Follow these guidelines to help StructureScan's AI detect structural issues like cracks, spalling, algae growth, and paint damage.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Internet Connection warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE0F2FF), RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFF2563EB), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(Icons.Filled.Wifi, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stable internet connection required to upload photos and receive AI-powered analysis results.",
                        color = Color(0xFF2563EB), fontSize = 13.5.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Do's and Don'ts Cards - Same Height
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Do's Card
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FADF)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier.padding(vertical = 16.dp, horizontal = 12.dp).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(40.dp).background(Color(0xFF34D399), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Text("Do's", color = Color(0xFF097B53), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Natural lighting")
                            DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Hold phone steady")
                            DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Multiple angles")
                            DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Clear focus")
                            DoDontRow(Icons.Outlined.Search, Color(0xFF34D399), "Close-up of damage")
                            DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Wide building shots")
                        }
                    }

                    // Don'ts Card
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(vertical = 16.dp, horizontal = 12.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Box(
                                Modifier.size(40.dp).background(Color(0xFFF87171), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Text("Don'ts", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Blurry images")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Use camera flash")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Block view")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Extreme angles")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Too dark/bright")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Important Notice Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Important Notice",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF996622)
                            )
                            Text(
                                "StructureScan provides preliminary assessments based on visible signs. This app is not a replacement for professional structural inspection. For serious concerns, always consult a licensed engineer.",
                                fontSize = 13.sp,
                                color = Color(0xFF996622),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Quick Steps section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).background(Color(0xFF2563EB), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1-4", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Quick Steps to Capture", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE6ECFE)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StepBox(Modifier.weight(1f).fillMaxHeight(), 1, "Overall structure view")
                            StepBox(Modifier.weight(1f).fillMaxHeight(), 2, "Capture from different sides")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StepBox(Modifier.weight(1f).fillMaxHeight(), 3, "Zoom on visible damage")
                            StepBox(Modifier.weight(1f).fillMaxHeight(), 4, "Review photo clarity")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // What AI Detects Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "What the AI Can Detect",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF2563EB)
                        )
                        Spacer(Modifier.height(8.dp))
                        DetectionRow("Cracks (major and minor severity)")
                        DetectionRow("Spalling and concrete deterioration")
                        DetectionRow("Algae growth and biological damage")
                        DetectionRow("Paint damage and surface issues")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Note: AI analyzes visible surface issues only. Hidden or internal defects cannot be detected.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                                            Modifier
                                                .height(8.dp)
                                                .width(if (idx == currentTip) 18.dp else 8.dp)
                                                .background(
                                                    if (idx == currentTip) Color(0xFFF9A826) else Color(0xFFFDE68A),
                                                    shape = RoundedCornerShape(5.dp)
                                                )
                                        )
                                        Spacer(Modifier.width(3.dp))
                                    }
                                }
                            }
                            Crossfade(targetState = currentTip, label = "tip") { tipIndex ->
                                Text(
                                    proTips[tipIndex],
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF996622),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// Helper for Do's/Don'ts row (left-aligned for Do's)
@Composable
fun DoDontRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 13.sp, color = Color.DarkGray)
    }
}

// Helper for centered Don'ts row
@Composable
fun DoDontRowCentered(icon: ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 13.sp, color = Color.DarkGray)
    }
}

@Composable
fun DetectionRow(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(Color(0xFF2563EB), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF1E40AF))
    }
}

@Composable
fun StepBox(modifier: Modifier = Modifier, num: Int, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(28.dp).background(Color(0xFF2563EB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$num", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuideScreenPreview() {
    GuideScreen({}, {})
}