package com.vonkernel.lit.searcher.domain.service

import com.vonkernel.lit.searcher.domain.exception.ArticleSearchException
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.model.SortType
import com.vonkernel.lit.searcher.domain.port.ArticleSearcher
import com.vonkernel.lit.searcher.domain.port.Embedder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticleSearchService(
    private val articleSearcher: ArticleSearcher,
    private val embedder: Embedder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun search(criteria: SearchCriteria): SearchResult {
        validate(criteria)
        return embedQueryOrNull(criteria).let { searchOrThrow(criteria, it) }
    }

    private suspend fun embedQueryOrNull(criteria: SearchCriteria): ByteArray? {
        if (!criteria.semanticSearch || criteria.query.isNullOrBlank()) return null

        return runCatching { embedder.embed(criteria.query) }
            .onFailure { log.warn("Query embedding failed, falling back to full-text search: {}", it.message) }
            .getOrNull()
    }

    private suspend fun searchOrThrow(criteria: SearchCriteria, queryEmbedding: ByteArray?): SearchResult =
        runCatching { articleSearcher.search(criteria, queryEmbedding) }
            .getOrElse { e -> throw ArticleSearchException("Search execution failed", e) }

    private fun validate(criteria: SearchCriteria) {
        if (criteria.sortBy == SortType.RELEVANCE && criteria.query.isNullOrBlank()) {
            throw ArticleSearchException("RELEVANCE sort requires a query")
        }
        if (criteria.sortBy == SortType.DISTANCE && criteria.proximity == null) {
            throw ArticleSearchException("DISTANCE sort requires a proximity filter")
        }
    }
}
