package com.example.structurescan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        val assessmentName =
            intent.getStringExtra(IntentKeys.ASSESSMENT_NAME) ?: "Unnamed Assessment"
        setContent {
            MaterialTheme {
                GuideScreen(
                    onBackClick = {
                        val intent = Intent(this, ScanActivity::class.java)
                        startActivity(intent)
                        finish()
                    },
                    onScanNow = {
                        val intent = Intent(this, BuildingAreaActivity::class.java)
                        intent.putExtra(IntentKeys.ASSESSMENT_NAME, assessmentName)
                        startActivity(intent)
                        finish()
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
        "Capture 3 or more photos per area from different angles so the AI can score risk reliably.",
        "Natural daylight gives the best results. Avoid flash and strong backlighting.",
        "Always take at least one wide shot of the whole wall or element plus close‑ups of visible damage.",
        "Hold the phone as level as you can. The tilt tool can compensate, but good technique improves accuracy."
    )
    var currentTip by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            currentTip = (currentTip + 1) % proTips.size
        }
    }

    Scaffold(
        bottomBar = {
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
                    Text(
                        "Start Taking Photos",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Top bar
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

                    Text(
                        text = "Create New Assessment",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0288D1),
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(paddingValues)
            ) {
                Spacer(Modifier.height(20.dp))

                // Hero icon
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Camera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "How StructureScan Works",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "StructureScan combines image analysis and device tilt sensing to give you a quick risk snapshot of a building after you capture photos.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                // “What this app offers” summary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "What you can do",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(Modifier.height(8.dp))
                        DetectionRow("Run Quick Scan to detect cracks, spalling, algae and paint damage per image.")
                        DetectionRow("Use Quick Tilt Analysis to measure how much a wall or column leans from vertical.")
                        DetectionRow("Score each building area (foundation, walls, roof, etc.) from MINOR to SEVERE risk.")
                        DetectionRow("Get an overall building status: INSPECTED (green), RESTRICTED (yellow), or UNSAFE (red).")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Internet requirement
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE0F2FF), RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFF2563EB), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Wifi,
                        null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Stable internet is required to upload photos and get AI‑powered results.",
                        color = Color(0xFF2563EB),
                        fontSize = 13.5.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Do / Don't cards (same layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FADF)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(vertical = 16.dp, horizontal = 12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF34D399), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Do's",
                                color = Color(0xFF097B53),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Use natural, even lighting")
                                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Hold phone steady and level")
                                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Capture 3–7 photos per area")
                                DoDontRow(Icons.Outlined.Check, Color(0xFF34D399), "Include both wide shots and close‑ups")
                                DoDontRow(Icons.Outlined.Search, Color(0xFF34D399), "Focus on visible cracks or damage")
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(vertical = 16.dp, horizontal = 12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF87171), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Don'ts",
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Blurry or shaky images")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Using camera flash or glare")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Obstructed views of the surface")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Extreme angles or tilted shots")
                                DoDontRow(Icons.Outlined.Close, Color(0xFFF87171), "Very dark or overexposed photos")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Risk scoring explanation (matches your logic)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "How risk scoring works",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1D4ED8)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Each photo is scored from 0–3 points based on the worst issue found:",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(Modifier.height(6.dp))
                        DetectionRow("3 pts (SEVERE): major cracks, spalling, or tilt >5°")
                        DetectionRow("2 pts (MODERATE): algae or tilt 2–5°")
                        DetectionRow("1 pt (MINOR): hairline cracks, paint damage, or mild tilt")
                        DetectionRow("0 pts (NONE): no visible issues and no tilt")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "If tilt is worse than surface damage, tilt wins. Area scores are based on the average points across photos, and the whole building is rated INSPECTED, RESTRICTED, or UNSAFE using ATC‑20 style logic.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Important notice / limitations
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
                                "Important limitations",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF996622)
                            )
                            Text(
                                "StructureScan looks only at what the camera sees and the device tilt. It cannot detect hidden or internal damage, soil problems, or detailed code compliance. Use it as a screening tool, not a final structural decision.",
                                fontSize = 13.sp,
                                color = Color(0xFF996622),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "For SEVERE or MODERATE results, or if you see worrying damage, always consult a licensed structural engineer.",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Quick steps (unchanged layout)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(Color(0xFF2563EB), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "1-4",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Quick steps to capture", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE6ECFE)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StepBox(
                                Modifier.weight(1f).fillMaxHeight(),
                                1,
                                "Capture the whole wall or element"
                            )
                            StepBox(
                                Modifier.weight(1f).fillMaxHeight(),
                                2,
                                "Repeat from another side or angle"
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StepBox(
                                Modifier.weight(1f).fillMaxHeight(),
                                3,
                                "Take close‑ups of any visible damage"
                            )
                            StepBox(
                                Modifier.weight(1f).fillMaxHeight(),
                                4,
                                "Check that photos are sharp before uploading"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // What AI detects (kept, lightly reworded)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "What the AI looks for",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF2563EB)
                        )
                        Spacer(Modifier.height(8.dp))
                        DetectionRow("Cracks (major and minor)")
                        DetectionRow("Spalling and concrete surface loss")
                        DetectionRow("Algae / biological staining")
                        DetectionRow("Paint peeling, flaking and surface wear")
                        DetectionRow("Out‑of‑plumb tilt from Quick Tilt Analysis")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "AI analysis is based on visible patterns only. It cannot see inside walls or foundations.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Pro tip card (same structure)
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
                            Icon(
                                Icons.Outlined.Lightbulb,
                                null,
                                tint = Color(0xFF6D4C00),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Pro Tip",
                                    color = Color(0xFF995000),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(8.dp))
                                Row {
                                    proTips.forEachIndexed { idx, _ ->
                                        Box(
                                            Modifier
                                                .height(8.dp)
                                                .width(if (idx == currentTip) 18.dp else 8.dp)
                                                .background(
                                                    if (idx == currentTip) Color(0xFFF9A826)
                                                    else Color(0xFFFDE68A),
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

                Spacer(Modifier.height(80.dp)) // space above bottom button
            }
        }
    }
}

// Helpers (unchanged)
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
                Modifier
                    .size(28.dp)
                    .background(Color(0xFF2563EB), CircleShape),
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
