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

    @Test
    fun `empty criteria produces match_all query`() {
        val criteria = SearchCriteria()
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        assertNotNull(request.query()?.matchAll())
    }

    @Test
    fun `RELEVANCE sort places query in must with multi_match`() {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.RELEVANCE)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.must().isNotEmpty())
        assertNotNull(boolQuery.must().first().multiMatch())

        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertNotNull(sort.first().score())
    }

    @Test
    fun `DATE sort places query in filter`() {
        val criteria = SearchCriteria(query = "폭우", sortBy = SortType.DATE)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.must().isEmpty())
        assertTrue(boolQuery.filter().any { it.multiMatch() != null })

        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertNotNull(sort.first().field())
        assertEquals("incidentDate", sort.first().field()?.field())
    }

    @Test
    fun `DISTANCE sort places query in filter with geo_distance sort`() {
        val criteria = SearchCriteria(
            query = "사고",
            sortBy = SortType.DISTANCE,
            proximity = ProximityFilter(35.647, 128.734, 10.0),
        )
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.multiMatch() != null })

        val sort = request.sort()
        assertTrue(sort.isNotEmpty())
        assertNotNull(sort.first().geoDistance())
    }

    @Test
    fun `semantic search adds knn to should clause`() {
        val criteria = SearchCriteria(query = "화재", semanticSearch = true, sortBy = SortType.RELEVANCE)
        val embedding = floatsToByteArray(FloatArray(128) { 0.1f })

        val request = SearchQueryBuilder.build(criteria, embedding, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.should().any { it.knn() != null })
    }

    @Test
    fun `jurisdiction code filter uses prefix query`() {
        val criteria = SearchCriteria(jurisdictionCode = "11680")
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.prefix() != null })
    }

    @Test
    fun `address query filter uses nested bool should`() {
        val criteria = SearchCriteria(addressQuery = "경북 청도군")
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.nested() != null })
    }

    @Test
    fun `region filter uses nested bool must with term queries`() {
        val criteria = SearchCriteria(region = RegionFilter(depth1Name = "경북", depth2Name = "청도군"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.nested() != null })
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
        assertTrue(boolQuery.filter().any { it.nested() != null })
    }

    @Test
    fun `incident types filter uses nested terms query`() {
        val criteria = SearchCriteria(incidentTypes = setOf("forest_fire", "typhoon"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.nested() != null })
    }

    @Test
    fun `urgency filter uses range query with gte`() {
        val criteria = SearchCriteria(urgencyLevel = 3)
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.range() != null })
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
        assertTrue(boolQuery.filter().any { it.range() != null })
    }

    @Test
    fun `date range filter with only dateFrom`() {
        val criteria = SearchCriteria(dateFrom = ZonedDateTime.parse("2025-06-01T00:00:00+09:00"))
        val request = SearchQueryBuilder.build(criteria, null, indexName)

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.filter().any { it.range() != null })
    }

    @Test
    fun `combined criteria produces correct query structure`() {
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

        val boolQuery = request.query()?.bool()
        assertNotNull(boolQuery)
        assertTrue(boolQuery.must().isNotEmpty())
        assertTrue(boolQuery.should().isNotEmpty())
        assertTrue(boolQuery.filter().size >= 3)
    }

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
