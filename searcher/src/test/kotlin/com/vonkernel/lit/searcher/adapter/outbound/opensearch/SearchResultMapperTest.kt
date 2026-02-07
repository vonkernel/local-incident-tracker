package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.HitsMetadata
import org.opensearch.client.opensearch.core.search.Hit
import org.opensearch.client.opensearch.core.search.TotalHits
import org.opensearch.client.opensearch.core.search.TotalHitsRelation
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchResultMapperTest {

    private val mapper = ObjectMapper()

    @Test
    fun `빈 응답을 빈 결과로 매핑한다`() {
        val response = mockSearchResponse(hits = emptyList(), totalHits = 0)

        val result = SearchResultMapper.map(response, 0, 20)

        assertEquals(0, result.totalHits)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.page)
        assertEquals(20, result.size)
    }

    @Test
    fun `검색 결과 문서를 도메인 모델로 매핑한다`() {
        val source = mapper.createObjectNode().apply {
            put("articleId", "art-001")
            put("title", "서울 폭우 피해")
            put("content", "서울 지역에 폭우가 내려 피해가 발생했습니다.")
            set<ObjectNode>("keywords", mapper.createArrayNode().add("폭우").add("서울"))
            set<ObjectNode>("incidentTypes", mapper.createArrayNode().add(
                mapper.createObjectNode().put("code", "heavy_rain").put("name", "폭우")
            ))
            set<ObjectNode>("urgency", mapper.createObjectNode().put("name", "긴급").put("level", 3))
            put("incidentDate", "2025-07-15T09:00:00+09:00")
            set<ObjectNode>("geoPoints", mapper.createArrayNode().add(
                mapper.createObjectNode().put("lat", 37.5665).put("lon", 126.9780)
            ))
            set<ObjectNode>("addresses", mapper.createArrayNode().add(
                mapper.createObjectNode()
                    .put("regionType", "BJDONG")
                    .put("code", "1168010100")
                    .put("addressName", "서울특별시 강남구 역삼1동")
                    .put("depth1Name", "서울")
                    .put("depth2Name", "강남구")
                    .put("depth3Name", "역삼1동")
            ))
            set<ObjectNode>("jurisdictionCodes", mapper.createArrayNode().add("1168010100"))
            put("writtenAt", "2025-07-15T08:30:00+09:00")
        }

        val hit = mockHit(source, 1.5f, mapOf("title" to listOf("<em>서울</em> 폭우 피해")))
        val response = mockSearchResponse(hits = listOf(hit), totalHits = 1)

        val result = SearchResultMapper.map(response, 0, 20)

        assertEquals(1, result.totalHits)
        assertEquals(1, result.items.size)

        val item = result.items.first()
        assertEquals("art-001", item.document.articleId)
        assertEquals("서울 폭우 피해", item.document.title)
        assertEquals(2, item.document.keywords?.size)
        assertEquals("heavy_rain", item.document.incidentTypes?.first()?.code)
        assertEquals(3, item.document.urgency?.level)
        assertNotNull(item.document.incidentDate)
        assertEquals(37.5665, item.document.geoPoints?.first()?.lat)
        assertEquals("서울", item.document.addresses?.first()?.depth1Name)
        assertEquals(1.5f, item.score)
        assertTrue(item.highlights.containsKey("title"))
    }

    @Test
    fun `페이지네이션 메타데이터를 올바르게 매핑한다`() {
        val response = mockSearchResponse(hits = emptyList(), totalHits = 100)

        val result = SearchResultMapper.map(response, 3, 10)

        assertEquals(100, result.totalHits)
        assertEquals(3, result.page)
        assertEquals(10, result.size)
    }

    @Test
    fun `null인 선택 필드를 정상 처리한다`() {
        val source = mapper.createObjectNode().apply {
            put("articleId", "art-002")
            putNull("sourceId")
            putNull("originId")
            putNull("title")
            putNull("content")
        }

        val hit = mockHit(source, null, emptyMap())
        val response = mockSearchResponse(hits = listOf(hit), totalHits = 1)

        val result = SearchResultMapper.map(response, 0, 20)

        val doc = result.items.first().document
        assertEquals("art-002", doc.articleId)
        assertEquals(null, doc.title)
        assertEquals(null, doc.content)
        assertEquals(null, doc.sourceId)
    }

    private fun mockSearchResponse(
        hits: List<Hit<ObjectNode>>,
        totalHits: Long,
    ): SearchResponse<ObjectNode> {
        val total = mockk<TotalHits>()
        every { total.value() } returns totalHits
        every { total.relation() } returns TotalHitsRelation.Eq

        val hitsMetadata = mockk<HitsMetadata<ObjectNode>>()
        every { hitsMetadata.hits() } returns hits
        every { hitsMetadata.total() } returns total

        val response = mockk<SearchResponse<ObjectNode>>()
        every { response.hits() } returns hitsMetadata

        return response
    }

    private fun mockHit(
        source: ObjectNode,
        score: Float?,
        highlights: Map<String, List<String>>,
    ): Hit<ObjectNode> {
        val hit = mockk<Hit<ObjectNode>>()
        every { hit.source() } returns source
        every { hit.score() } returns score?.toDouble()
        every { hit.highlight() } returns highlights
        return hit
    }
}
