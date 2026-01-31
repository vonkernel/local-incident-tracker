package com.vonkernel.lit.searcher.domain.port

import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SearchResult

interface ArticleSearcher {
    suspend fun search(criteria: SearchCriteria, queryEmbedding: ByteArray? = null): SearchResult
}
