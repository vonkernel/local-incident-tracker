package com.vonkernel.lit.searcher.domain.model

import java.time.ZonedDateTime

data class SearchCriteria(
    // 텍스트 검색
    val query: String? = null,
    val semanticSearch: Boolean = false,

    // 위치 기반 필터
    val jurisdictionCode: String? = null,
    val addressQuery: String? = null,
    val region: RegionFilter? = null,
    val proximity: ProximityFilter? = null,

    // 필터
    val incidentTypes: Set<String>? = null,
    val urgencyLevel: Int? = null,
    val dateFrom: ZonedDateTime? = null,
    val dateTo: ZonedDateTime? = null,

    // 정렬
    val sortBy: SortType = SortType.DATE,

    // 페이지네이션
    val page: Int = 0,
    val size: Int = 20,
)

enum class SortType {
    RELEVANCE,
    DATE,
    DISTANCE,
}

data class ProximityFilter(
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
)

data class RegionFilter(
    val depth1Name: String? = null,
    val depth2Name: String? = null,
    val depth3Name: String? = null,
)
