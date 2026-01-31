package com.vonkernel.lit.analyzer.domain.port.geocoding

import com.vonkernel.lit.core.entity.Location

interface Geocoder {
    suspend fun geocodeByAddress(query: String): List<Location>
    suspend fun geocodeByKeyword(query: String): List<Location>
}