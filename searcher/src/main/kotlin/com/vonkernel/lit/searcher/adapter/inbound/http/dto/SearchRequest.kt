package com.vonkernel.lit.searcher.adapter.inbound.http.dto

import com.vonkernel.lit.searcher.domain.exception.InvalidSearchRequestException
import com.vonkernel.lit.searcher.domain.model.ProximityFilter
import com.vonkernel.lit.searcher.domain.model.RegionFilter
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SortType

data class SearchRequest(
    val query: String? = null,
    val semanticSearch: Boolean = false,

    val jurisdictionCode: String? = null,
    val addressQuery: String? = null,

    val depth1Name: String? = null,
    val depth2Name: String? = null,
    val depth3Name: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null,

    val incidentTypes: Set<String>? = null,
    val urgencyLevel: Int? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,

    val sortBy: String? = null,

    val page: Int = 0,
    val size: Int = 20,
) {

    fun toCriteria(): SearchCriteria {
        validate()

        val region = if (depth1Name != null || depth2Name != null || depth3Name != null) {
            RegionFilter(depth1Name, depth2Name, depth3Name)
        } else null

        val proximity = if (latitude != null && longitude != null && distanceKm != null) {
            ProximityFilter(latitude, longitude, distanceKm)
        } else null

        return SearchCriteria(
            query = query,
            semanticSearch = semanticSearch,
            jurisdictionCode = jurisdictionCode,
            addressQuery = addressQuery,
            region = region,
            proximity = proximity,
            incidentTypes = incidentTypes,
            urgencyLevel = urgencyLevel,
            dateFrom = dateFrom?.let { java.time.ZonedDateTime.parse(it) },
            dateTo = dateTo?.let { java.time.ZonedDateTime.parse(it) },
            sortBy = sortBy?.let { SortType.valueOf(it.uppercase()) } ?: SortType.DATE,
            page = page,
            size = size,
        )
    }

    private fun validate() {
        val proximityFields = listOf("latitude" to latitude, "longitude" to longitude, "distanceKm" to distanceKm)
        val presentFields = proximityFields.filter { it.second != null }
        if (presentFields.isNotEmpty() && presentFields.size < 3) {
            val missing = proximityFields.filter { it.second == null }.map { it.first }
            throw InvalidSearchRequestException("proximity 필터에는 latitude, longitude, distanceKm이 모두 필요합니다. 누락: $missing")
        }

        if (distanceKm != null && distanceKm <= 0) {
            throw InvalidSearchRequestException("distanceKm은 0보다 커야 합니다")
        }

        if (page < 0) {
            throw InvalidSearchRequestException("page는 0 이상이어야 합니다")
        }

        if (size !in 1..100) {
            throw InvalidSearchRequestException("size는 1~100 범위여야 합니다")
        }
    }
}
