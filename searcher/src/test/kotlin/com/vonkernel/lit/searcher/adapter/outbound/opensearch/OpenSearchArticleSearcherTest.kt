package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SortType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.HitsMetadata
import org.opensearch.client.opensearch.core.search.TotalHits
import org.opensearch.client.opensearch.core.search.TotalHitsRelation
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenSearchArticleSearcherTest {

    private val openSearchClient = mockk<OpenSearchClient>()
    private val searcher = OpenSearchArticleSearcher(openSearchClient, "test-articles")
    private val mapper = ObjectMapper()

    @Test
    fun `search는 OpenSearch 클라이언트에 위임하고 결과를 매핑한다`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.DATE)

        val total = mockk<TotalHits>()
        every { total.value() } returns 0L
        every { total.relation() } returns TotalHitsRelation.Eq

        val hitsMetadata = mockk<HitsMetadata<ObjectNode>>()
        every { hitsMetadata.hits() } returns emptyList()
        every { hitsMetadata.total() } returns total

        val response = mockk<SearchResponse<ObjectNode>>()
        every { response.hits() } returns hitsMetadata

        val requestSlot = slot<SearchRequest>()
        every { openSearchClient.search(capture(requestSlot), ObjectNode::class.java) } returns response

        val result = searcher.search(criteria, null)

        assertNotNull(result)
        assertEquals(0, result.totalHits)
        assertEquals("test-articles", requestSlot.captured.index().first())
    }

    @Test
    fun `search는 쿼리 임베딩을 쿼리 빌더에 전달한다`() = runTest {
        val criteria = SearchCriteria(query = "화재", semanticSearch = true, sortBy = SortType.RELEVANCE)
        val embedding = ByteArray(512) { 0 }

        val source = mapper.createObjectNode().apply {
            put("articleId", "art-001")
            putNull("sourceId")
            putNull("originId")
            putNull("title")
            putNull("content")
        }

        val hit = mockk<org.opensearch.client.opensearch.core.search.Hit<ObjectNode>>()
        every { hit.source() } returns source
        every { hit.score() } returns 1.0
        every { hit.highlight() } returns emptyMap()

        val total = mockk<TotalHits>()
        every { total.value() } returns 1L
        every { total.relation() } returns TotalHitsRelation.Eq

        val hitsMetadata = mockk<HitsMetadata<ObjectNode>>()
        every { hitsMetadata.hits() } returns listOf(hit)
        every { hitsMetadata.total() } returns total

        val response = mockk<SearchResponse<ObjectNode>>()
        every { response.hits() } returns hitsMetadata

        every { openSearchClient.search(any<SearchRequest>(), ObjectNode::class.java) } returns response

        val result = searcher.search(criteria, embedding)

        assertEquals(1, result.totalHits)
        assertEquals("art-001", result.items.first().document.articleId)
    }
}
