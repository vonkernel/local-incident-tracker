package com.vonkernel.lit.searcher.domain.model

import com.vonkernel.lit.core.entity.ArticleIndexDocument

data class SearchResult(
    val items: List<SearchResultItem>,
    val totalHits: Long,
    val page: Int,
    val size: Int,
)

data class SearchResultItem(
    val document: ArticleIndexDocument,
    val score: Float?,
    val highlights: Map<String, List<String>> = emptyMap(),
)
