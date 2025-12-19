package com.example.structurescan

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Photo metadata including optional location name
 */
@Parcelize
data class PhotoMetadata(
    val uri: Uri,
    val locationName: String = "",  // Optional specific location within the area
    val tilt: TiltMeasurement? = null,
    val capturedAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Updated BuildingArea with PhotoMetadata
 */
@Parcelize
data class BuildingArea(
    val id: String,
    val name: String,
    val description: String = "",
    val areaType: AreaType = AreaType.OTHER,
    val requiresStructuralTilt: Boolean = false,
    val photoMetadata: List<PhotoMetadata> = emptyList(),  // ✅ NEW: Stores photos with metadata
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    // Helper property for backward compatibility
    val photos: List<Uri> get() = photoMetadata.map { it.uri }
    val photoTilts: List<TiltMeasurement?> get() = photoMetadata.map { it.tilt }
}
