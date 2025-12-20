// Create a new file: Constants.kt
package com.example.structurescan

object IntentKeys {
    const val ASSESSMENT_NAME = "assessment_name"
    const val FINAL_IMAGES = "final_images"
    const val CAPTURED_IMAGES = "captured_images"
    const val UPDATED_IMAGES = "updated_images"

    const val BUILDING_AREAS = "building_areas"

    // Building Information (ATC-20 compliant)
    const val BUILDING_TYPE = "building_type"
    const val ADDRESS = "address"                    // ✅ NEW: ATC-20 "Address"
    const val FOOTPRINT_AREA = "footprint_area"     // ✅ NEW: ATC-20 "Footprint area (sq ft)"
    const val CONSTRUCTION_YEAR = "construction_year"
    const val RENOVATION_YEAR = "renovation_year"
    const val FLOORS = "floors"
    const val TYPE_OF_CONSTRUCTION = "type_of_construction"  // ✅ NEW: ATC-20 checkboxes
    const val MATERIAL = "material"
    const val FOUNDATION = "foundation"
    const val ENVIRONMENT = "environment"
    const val PREVIOUS_ISSUES = "previous_issues"
    const val OCCUPANCY = "occupancy"
    const val ENVIRONMENTAL_RISKS = "environmental_risks"
    const val NOTES = "notes"
}