package com.example.structurescan

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Updated BuildingArea with optional structural tilt analysis
 */
@Parcelize
data class BuildingArea(
    val id: String,
    val name: String,
    val description: String = "",
    val areaType: AreaType = AreaType.OTHER,
    val requiresStructuralTilt: Boolean = false,  // ✅ NEW: Optional tilt analysis
    val photos: List<Uri> = emptyList(),
    val photoTilts: List<TiltMeasurement?> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
