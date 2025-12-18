package com.example.structurescan

/**
 * Building area types with structural analysis recommendations
 */
enum class AreaType(
    val displayName: String,
    val suggestTiltAnalysis: Boolean,
    val description: String
) {
    FOUNDATION(
        displayName = "Foundation",
        suggestTiltAnalysis = true,
        description = "Building foundation and footing"
    ),
    EXTERIOR_WALLS(
        displayName = "Exterior Walls",
        suggestTiltAnalysis = true,
        description = "Outer load-bearing walls"
    ),
    LOAD_BEARING_WALLS(
        displayName = "Load-Bearing Walls",
        suggestTiltAnalysis = true,
        description = "Interior structural walls"
    ),
    COLUMNS(
        displayName = "Columns/Pillars",
        suggestTiltAnalysis = true,
        description = "Vertical support structures"
    ),
    BASEMENT(
        displayName = "Basement",
        suggestTiltAnalysis = true,
        description = "Below-grade structure"
    ),
    ROOF(
        displayName = "Roof",
        suggestTiltAnalysis = false,
        description = "Roof surface and structure"
    ),
    INTERIOR_WALLS(
        displayName = "Interior Walls",
        suggestTiltAnalysis = false,
        description = "Non-load-bearing partition walls"
    ),
    CEILING(
        displayName = "Ceiling",
        suggestTiltAnalysis = false,
        description = "Interior ceiling finishes"
    ),
    FLOORS(
        displayName = "Floors",
        suggestTiltAnalysis = false,
        description = "Floor surfaces and finishes"
    ),
    WINDOWS_DOORS(
        displayName = "Windows & Doors",
        suggestTiltAnalysis = false,
        description = "Openings and fixtures"
    ),
    PLUMBING(
        displayName = "Plumbing",
        suggestTiltAnalysis = false,
        description = "Water and drainage systems"
    ),
    ELECTRICAL(
        displayName = "Electrical",
        suggestTiltAnalysis = false,
        description = "Electrical systems and fixtures"
    ),
    HVAC(
        displayName = "HVAC",
        suggestTiltAnalysis = false,
        description = "Heating, ventilation, and air conditioning"
    ),
    OTHER(
        displayName = "Other",
        suggestTiltAnalysis = false,
        description = "Other building components"
    );
}
