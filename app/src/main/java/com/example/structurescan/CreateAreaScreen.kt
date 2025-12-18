package com.example.structurescan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAreaScreen(
    onAreaCreated: (BuildingArea) -> Unit,
    onBack: () -> Unit
) {
    var areaName by remember { mutableStateOf("") }
    var areaDescription by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AreaType.OTHER) }
    var enableTiltAnalysis by remember { mutableStateOf(false) }

    // Auto-suggest based on area type
    LaunchedEffect(selectedType) {
        enableTiltAnalysis = selectedType.suggestTiltAnalysis
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Building Area") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Area Name
            OutlinedTextField(
                value = areaName,
                onValueChange = { areaName = it },
                label = { Text("Area Name") },
                placeholder = { Text("e.g., North Foundation Wall") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Home, contentDescription = null)
                }
            )

            Spacer(Modifier.height(16.dp))

            // Area Type Selector
            Text(
                "Area Type",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            AreaTypeDropdown(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                selectedType.description,
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(16.dp))

            // Description (Optional)
            OutlinedTextField(
                value = areaDescription,
                onValueChange = { areaDescription = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Additional notes about this area") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(Modifier.height(24.dp))

            // Structural Tilt Analysis Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (enableTiltAnalysis) {
                        Color(0xFFEFF6FF)
                    } else {
                        Color(0xFFF9FAFB)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Architecture,
                                    contentDescription = null,
                                    tint = if (enableTiltAnalysis) {
                                        Color(0xFF2563EB)
                                    } else {
                                        Color.Gray
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Structural Tilt Analysis",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                if (selectedType.suggestTiltAnalysis) {
                                    "✅ Recommended for this area type"
                                } else {
                                    "Optional - not typically needed"
                                },
                                fontSize = 12.sp,
                                color = if (selectedType.suggestTiltAnalysis) {
                                    Color(0xFF059669)
                                } else {
                                    Color.Gray
                                }
                            )
                        }

                        Switch(
                            checked = enableTiltAnalysis,
                            onCheckedChange = { enableTiltAnalysis = it }
                        )
                    }

                    AnimatedVisibility(visible = enableTiltAnalysis) {
                        Column {
                            Spacer(Modifier.height(12.dp))

                            // Disclaimer
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Preliminary screening only. Results are estimates " +
                                                "(±1-2° accuracy). For critical structural concerns, " +
                                                "consult a licensed structural engineer.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Create Button
            Button(
                onClick = {
                    val area = BuildingArea(
                        id = UUID.randomUUID().toString(),
                        name = areaName,
                        description = areaDescription,
                        areaType = selectedType,
                        requiresStructuralTilt = enableTiltAnalysis
                    )
                    onAreaCreated(area)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = areaName.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create Area")
            }

            if (areaName.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please enter an area name",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaTypeDropdown(
    selectedType: AreaType,
    onTypeSelected: (AreaType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedType.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AreaType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(type.displayName)
                            if (type.suggestTiltAnalysis) {
                                Icon(
                                    Icons.Default.Architecture,
                                    contentDescription = "Structural analysis recommended",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}
