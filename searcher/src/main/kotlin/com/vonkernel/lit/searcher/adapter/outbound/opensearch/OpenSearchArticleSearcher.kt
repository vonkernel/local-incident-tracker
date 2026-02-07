package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.fasterxml.jackson.databind.node.ObjectNode
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.port.ArticleSearcher
import org.opensearch.client.opensearch.OpenSearchClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OpenSearchArticleSearcher(
    private val openSearchClient: OpenSearchClient,
    @param:Value("\${opensearch.index-name:articles}") private val indexName: String,
) : ArticleSearcher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun search(criteria: SearchCriteria, queryEmbedding: ByteArray?): SearchResult =
        SearchQueryBuilder.build(criteria, queryEmbedding, indexName)
            .also { log.debug("Executing search on index '{}' with criteria: {}", indexName, criteria) }
            .let { openSearchClient.search(it, ObjectNode::class.java) }
            .also { log.debug("Search returned {} hits", it.hits().total()?.value()) }
            .let { SearchResultMapper.map(it, criteria.page, criteria.size) }
}
