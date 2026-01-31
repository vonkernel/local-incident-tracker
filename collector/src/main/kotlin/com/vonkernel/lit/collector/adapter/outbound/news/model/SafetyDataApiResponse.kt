package com.vonkernel.lit.collector.adapter.outbound.news.model

/**
 * Response wrapper from Safety Data API.
 *
 * Maps to the actual API response structure from:
 * https://www.safetydata.go.kr/V2/api/DSSP-IF-00051
 */
data class SafetyDataApiResponse(
    val header: SafetyDataApiResponseHeader,
    val numOfRows: Int,
    val pageNo: Int,
    val totalCount: Int,
    val body: List<YonhapnewsArticle>
)
