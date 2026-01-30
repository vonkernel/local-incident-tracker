package com.vonkernel.lit.analyzer.domain.model

data class LocationValidationInput(
    val title: String,
    val content: String,
    val extractedLocations: String
)
