package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.vonkernel.lit.searcher.domain.model.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchQueryBuilderTest {

    private val indexName = "test-articles"

    // --- 기본 쿼리 ---

    @Test
    fun `empty criteria produces match_all query`() {
        val criteria = SearchCriteria()
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertNotNull(request.query()?.matchAll())
    }

    @Test
    fun `text only RELEVANCE sort produces multiMatch in must`() {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.RELEVANCE)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertNotNull(request.query()?.multiMatch())
        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertNotNull(sort.first().score())
    }

    @Test
    fun `DATE sort with text produces multiMatch directly`() {
        val criteria = SearchCriteria(query = "폭우", sortBy = SortType.DATE)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertNotNull(request.query()?.multiMatch())
        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertEquals("incidentDate", sort.first().field()?.field())
    }

    @Test
    fun `DISTANCE sort with text produces multiMatch and geo_distance sort`() {
        val criteria = SearchCriteria(
            query = "사고",
            sortBy = SortType.DISTANCE,
            proximity = ProximityFilter(35.647, 128.734, 10.0),
        )
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        // text + proximity filter → bool { must: [multiMatch], filter: [geoDistance] }
        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.must().any { it.isMultiMatch })
        assertTrue(boolQuery.filter().isNotEmpty())

        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertNotNull(sort.first().geoDistance())
    }

    // --- Semantic search ---

    @Test
    fun `semantic search uses hybrid query with knn and multiMatch sub-queries`() {
        val criteria = SearchCriteria(query = "화재", semanticSearch = true, sortBy = SortType.RELEVANCE)
        val embedding = floatsToByteArray(FloatArray(128) { 0.1f })

        val request = SearchQueryBuilder.build(criteria, embedding, indexName)

        val hybridQuery = request.query()?.hybrid()
        assertNotNull(hybridQuery)
        assertEquals(2, hybridQuery.queries().size)
    }

    @Test
    fun `semantic search with filters applies pre-filters to hybrid sub-queries`() {
        val criteria = SearchCriteria(
            query = "폭우 피해",
            semanticSearch = true,
            jurisdictionCode = "11",
            incidentTypes = setOf("heavy_rain"),
            urgencyLevel = 2,
            dateFrom = ZonedDateTime.parse("2025-07-01T00:00:00+09:00"),
            sortBy = SortType.RELEVANCE,
        )
        val embedding = floatsToByteArray(FloatArray(128) { 0.5f })
        val request = SearchQueryBuilder.build(criteria, embedding, indexName)

        // top level: hybrid (필터가 sub-query 내부에 pre-filter로 적용)
        val hybridQuery = request.query()?.hybrid()
        assertNotNull(hybridQuery)
        assertEquals(2, hybridQuery.queries().size)

        // BM25 sub-query: bool { must: [multiMatch], filter: [...], boost: 0.2 }
        val bm25Sub = hybridQuery.queries().first().bool()
        assertNotNull(bm25Sub)
        assertTrue(bm25Sub.must().any { it.isMultiMatch })
        assertTrue(bm25Sub.filter().size >= 3)

        // kNN sub-query: bool { must: [knn(filter: {...})], boost: 10.0 }
        val knnSub = hybridQuery.queries()[1].bool()
        assertNotNull(knnSub)
        val knnQuery = knnSub.must().first { it.isKnn }.knn()
        assertNotNull(knnQuery.filter())
    }

    // --- 필터 ---

    @Test
    fun `jurisdiction code filter uses prefix query`() {
        val criteria = SearchCriteria(jurisdictionCode = "11680")
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isPrefix })
    }

    @Test
    fun `address query filter uses nested bool should`() {
        val criteria = SearchCriteria(addressQuery = "경북 청도군")
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isNested })
    }

    @Test
    fun `region filter uses nested bool must with term queries`() {
        val criteria = SearchCriteria(region = RegionFilter(depth1Name = "경북", depth2Name = "청도군"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isNested })
    }

    @Test
    fun `region filter with only depth1Name produces single term query`() {
        val criteria = SearchCriteria(region = RegionFilter(depth1Name = "서울"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().isNotEmpty())
    }

    @Test
    fun `empty region filter is ignored`() {
        val criteria = SearchCriteria(region = RegionFilter())
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertNotNull(request.query()?.matchAll())
    }

    @Test
    fun `proximity filter uses nested geo_distance query`() {
        val criteria = SearchCriteria(proximity = ProximityFilter(37.5665, 126.9780, 25.0))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isNested })
    }

    @Test
    fun `incident types filter uses nested terms query`() {
        val criteria = SearchCriteria(incidentTypes = setOf("forest_fire", "typhoon"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isNested })
    }

    @Test
    fun `urgency filter uses range query with gte`() {
        val criteria = SearchCriteria(urgencyLevel = 3)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isRange })
    }

    @Test
    fun `date range filter uses range query`() {
        val criteria = SearchCriteria(
            dateFrom = ZonedDateTime.parse("2025-01-01T00:00:00+09:00"),
            dateTo = ZonedDateTime.parse("2025-12-31T23:59:59+09:00"),
        )
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isRange })
    }

    @Test
    fun `date range filter with only dateFrom`() {
        val criteria = SearchCriteria(dateFrom = ZonedDateTime.parse("2025-06-01T00:00:00+09:00"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.isRange })
    }

    // --- 텍스트 + 필터 결합 ---

    @Test
    fun `text query with filters wraps in bool must + filter`() {
        val criteria = SearchCriteria(
            query = "화재",
            jurisdictionCode = "11680",
            sortBy = SortType.RELEVANCE,
        )
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.must().any { it.isMultiMatch })
        assertTrue(boolQuery.filter().any { it.isPrefix })
    }

    // --- 페이지네이션 & 하이라이트 ---

    @Test
    fun `pagination is correctly applied`() {
        val criteria = SearchCriteria(page = 2, size = 10)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertEquals(20, request.from())
        assertEquals(10, request.size())
    }

    @Test
    fun `highlight fields are configured`() {
        val criteria = SearchCriteria()
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val highlightFields = request.highlight()?.fields()?.keys
        assertNotNull(highlightFields)
        assertTrue(highlightFields.contains("title"))
        assertTrue(highlightFields.contains("content"))
        assertTrue(highlightFields.contains("keywords"))
    }

    private fun floatsToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.BIG_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
