package com.vonkernel.lit.analyzer.adapter.outbound.geocoding

import com.vonkernel.lit.core.entity.Location

interface GeocodingClient {
    suspend fun geocodeByAddress(query: String): List<Location>
    suspend fun geocodeByKeyword(query: String): List<Location>
}
