package com.vonkernel.lit.searcher.adapter.inbound.http.dto

import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.model.SearchResultItem

data class SearchResponse(
    val items: List<SearchItemResponse>,
    val totalHits: Long,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun from(result: SearchResult): SearchResponse = SearchResponse(
            items = result.items.map { SearchItemResponse.from(it) },
            totalHits = result.totalHits,
            page = result.page,
            size = result.size,
        )
    }
}

data class SearchItemResponse(
    val articleId: String,
    val title: String?,
    val content: String?,
    val keywords: List<String>?,
    val incidentTypes: List<IncidentTypeResponse>?,
    val urgency: UrgencyResponse?,
    val incidentDate: String?,
    val addresses: List<AddressResponse>?,
    val score: Float?,
    val highlights: Map<String, List<String>>,
) {
    companion object {
        fun from(item: SearchResultItem): SearchItemResponse {
            val doc = item.document
            return SearchItemResponse(
                articleId = doc.articleId,
                title = doc.title,
                content = doc.content,
                keywords = doc.keywords,
                incidentTypes = doc.incidentTypes?.map { IncidentTypeResponse(it.code, it.name) },
                urgency = doc.urgency?.let { UrgencyResponse(it.name, it.level) },
                incidentDate = doc.incidentDate?.toString(),
                addresses = doc.addresses?.map { addr ->
                    AddressResponse(
                        addressName = addr.addressName,
                        depth1Name = addr.depth1Name,
                        depth2Name = addr.depth2Name,
                        depth3Name = addr.depth3Name,
                    )
                },
                score = item.score,
                highlights = item.highlights,
            )
        }
    }
}

data class IncidentTypeResponse(val code: String, val name: String)
data class UrgencyResponse(val name: String, val level: Int)
data class AddressResponse(
    val addressName: String,
    val depth1Name: String?,
    val depth2Name: String?,
    val depth3Name: String?,
)
