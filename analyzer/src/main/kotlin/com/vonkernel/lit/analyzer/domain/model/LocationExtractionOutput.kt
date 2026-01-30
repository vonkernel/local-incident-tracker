package com.vonkernel.lit.analyzer.domain.model

data class LocationExtractionOutput(
    val location: ExtractedLocation
)

data class ExtractedLocation(
    val name: String,
    val type: LocationType
)

enum class LocationType {
    ADDRESS,
    LANDMARK,
    UNRESOLVABLE
}