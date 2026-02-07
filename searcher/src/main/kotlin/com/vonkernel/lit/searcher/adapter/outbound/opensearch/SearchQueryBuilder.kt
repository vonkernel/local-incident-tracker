package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.vonkernel.lit.searcher.domain.model.ProximityFilter
import com.vonkernel.lit.searcher.domain.model.RegionFilter
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SortType
import org.opensearch.client.json.JsonData
import org.opensearch.client.opensearch._types.*
import org.opensearch.client.opensearch._types.query_dsl.Operator
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType
import org.opensearch.client.opensearch.core.SearchRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.format.DateTimeFormatter

object SearchQueryBuilder {

    /** cosinesimil score 0.8 = cosine similarity ≥ 0.6 */
    private const val KNN_MIN_SCORE = 0.8f

    /** semantic search 시 BM25:kNN ≈ 20:80 비중 조정 */
    private const val BM25_BOOST_WITH_SEMANTIC = 0.2f
    private const val KNN_BOOST = 10.0f

    /** hybrid 쿼리 점수 정규화를 위한 search pipeline */
    private const val HYBRID_SEARCH_PIPELINE = "hybrid-search-pipeline"

    fun build(criteria: SearchCriteria, queryEmbedding: ByteArray?, indexName: String): SearchRequest {
        val filterQueries = buildFilters(criteria)

        return resolveTopLevelQuery(
                textQuery = buildTextQuery(criteria, queryEmbedding, filterQueries),
                filterQueries = filterQueries,
                isHybrid = queryEmbedding != null
            )
            .let { buildSearchRequest(it, criteria, queryEmbedding != null, indexName) }
    }

    // ========== Top-Level Query Resolution ==========

    private fun resolveTopLevelQuery(textQuery: Query?, filterQueries: List<Query>, isHybrid: Boolean): Query =
        when {
            textQuery != null && filterQueries.isNotEmpty() && !isHybrid ->
                Query.Builder().bool { b -> b.must(textQuery).filter(filterQueries) }.build()
            textQuery != null -> textQuery
            filterQueries.isNotEmpty() ->
                Query.Builder().bool { b -> b.filter(filterQueries) }.build()
            else ->
                Query.Builder().matchAll { it }.build()
        }

    private fun buildSearchRequest(
        query: Query, criteria: SearchCriteria, isHybrid: Boolean, indexName: String
    ): SearchRequest =
        SearchRequest.Builder()
            .index(indexName)
            .query(query)
            .from(criteria.page * criteria.size)
            .size(criteria.size)
            .sort(buildSort(criteria))
            .highlight { h ->
                h.fields("title") { it }
                    .fields("content") { it }
                    .fields("keywords") { it }
            }
            .apply { if (isHybrid) pipeline(HYBRID_SEARCH_PIPELINE) }
            .build()

    // ========== Text Query (BM25 / kNN / Hybrid) ==========

    private fun buildTextQuery(
        criteria: SearchCriteria,
        queryEmbedding: ByteArray?,
        filterQueries: List<Query>,
    ): Query? {
        val multiMatch = criteria.query?.takeIf { it.isNotBlank() }
            ?.let { buildMultiMatchQuery(it) }
            ?: return null

        return queryEmbedding
            ?.let { buildKnnQuery(it, filterQueries) }
            ?.let { buildHybridQuery(multiMatch, it, filterQueries) }
            ?: multiMatch
    }

    private fun buildMultiMatchQuery(query: String): Query =
        Query.Builder().multiMatch { mm ->
            mm.query(query)
                .fields("title^3", "keywords^2", "content")
                .operator(Operator.And)
                .type(TextQueryType.BestFields)
        }.build()

    private fun buildKnnQuery(queryEmbedding: ByteArray, filterQueries: List<Query>): Query =
        Query.Builder().knn { knn ->
            knn.field("contentEmbedding")
                .vector(byteArrayToFloats(queryEmbedding))
                .minScore(KNN_MIN_SCORE)
            filterQueries.takeIf { it.isNotEmpty() }
                ?.let { Query.Builder().bool { b -> b.filter(it) }.build() }
                ?.let { knn.filter(it) }
            knn
        }.build()

    private fun buildHybridQuery(multiMatch: Query, knnQuery: Query, filterQueries: List<Query>): Query {
        val bm25Boosted = Query.Builder().bool { b ->
            b.must(multiMatch).boost(BM25_BOOST_WITH_SEMANTIC)
            filterQueries.takeIf { it.isNotEmpty() }?.let { b.filter(it) }
            b
        }.build()
        val knnBoosted = Query.Builder().bool { b ->
            b.must(knnQuery).boost(KNN_BOOST)
        }.build()
        return Query.Builder().hybrid { h -> h.queries(bm25Boosted, knnBoosted) }.build()
    }

    // ========== Filters ==========

    private fun buildFilters(criteria: SearchCriteria): List<Query> = listOfNotNull(
        buildJurisdictionCodeFilter(criteria.jurisdictionCode),
        buildAddressQueryFilter(criteria.addressQuery),
        buildRegionFilter(criteria.region),
        buildProximityFilter(criteria.proximity),
        buildIncidentTypesFilter(criteria.incidentTypes),
        buildUrgencyFilter(criteria.urgencyLevel),
        buildDateRangeFilter(criteria),
    )

    private fun buildJurisdictionCodeFilter(jurisdictionCode: String?): Query? {
        if (jurisdictionCode == null) return null

        return Query.Builder().prefix { p ->
            p.field("jurisdictionCodes").value(jurisdictionCode)
        }.build()
    }

    private fun buildAddressQueryFilter(addressQuery: String?): Query? {
        if (addressQuery == null) return null

        return Query.Builder().nested { nested ->
            nested.path("addresses").query { q ->
                q.bool { b ->
                    b.minimumShouldMatch("1")
                        .should(Query.Builder().match { m ->
                            m.field("addresses.addressName").query(FieldValue.of(addressQuery))
                        }.build())
                        .should(Query.Builder().term { t ->
                            t.field("addresses.depth1Name").value(FieldValue.of(addressQuery))
                        }.build())
                        .should(Query.Builder().term { t ->
                            t.field("addresses.depth2Name").value(FieldValue.of(addressQuery))
                        }.build())
                        .should(Query.Builder().term { t ->
                            t.field("addresses.depth3Name").value(FieldValue.of(addressQuery))
                        }.build())
                }
            }
        }.build()
    }

    private fun buildRegionFilter(region: RegionFilter?): Query? {
        if (region == null) return null

        val mustClauses = listOfNotNull(
            region.depth1Name?.let { buildTermQuery("addresses.depth1Name", it) },
            region.depth2Name?.let { buildTermQuery("addresses.depth2Name", it) },
            region.depth3Name?.let { buildTermQuery("addresses.depth3Name", it) },
        )

        if (mustClauses.isEmpty()) return null

        return Query.Builder().nested { nested ->
            nested.path("addresses").query { q ->
                q.bool { b -> b.must(mustClauses) }
            }
        }.build()
    }

    private fun buildTermQuery(field: String, value: String): Query =
        Query.Builder().term { t -> t.field(field).value(FieldValue.of(value)) }.build()

    private fun buildProximityFilter(proximity: ProximityFilter?): Query? {
        if (proximity == null) return null

        return Query.Builder().nested { nested ->
            nested.path("geoPoints").query { q ->
                q.geoDistance { geo ->
                    geo.field("geoPoints.location")
                        .location { loc -> loc.latlon { ll -> ll.lat(proximity.latitude).lon(proximity.longitude) } }
                        .distance("${proximity.distanceKm}km")
                }
            }
        }.build()
    }

    private fun buildIncidentTypesFilter(incidentTypes: Set<String>?): Query? {
        if (incidentTypes.isNullOrEmpty()) return null

        return Query.Builder().nested { nested ->
            nested.path("incidentTypes").query { q ->
                q.terms { t ->
                    t.field("incidentTypes.code")
                        .terms { tv -> tv.value(incidentTypes.map { FieldValue.of(it) }) }
                }
            }
        }.build()
    }

    private fun buildUrgencyFilter(urgencyLevel: Int?): Query? {
        if (urgencyLevel == null) return null

        return Query.Builder().range { r ->
            r.field("urgency.level").gte(JsonData.of(urgencyLevel))
        }.build()
    }

    private fun buildDateRangeFilter(criteria: SearchCriteria): Query? {
        if (criteria.dateFrom == null && criteria.dateTo == null) return null

        return Query.Builder().range { r ->
            r.field("incidentDate").apply {
                criteria.dateFrom?.let { gte(JsonData.of(it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) }
                criteria.dateTo?.let { lte(JsonData.of(it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) }
            }
        }.build()
    }

    // ========== Sort ==========

    private fun buildSort(criteria: SearchCriteria): List<SortOptions> = when (criteria.sortBy) {
        SortType.RELEVANCE -> listOf(buildScoreSort())
        SortType.DATE -> listOf(buildDateSort())
        SortType.DISTANCE -> listOf(buildDistanceSort(criteria.proximity!!))
    }

    private fun buildScoreSort(): SortOptions =
        SortOptions.Builder().score { it.order(SortOrder.Desc) }.build()

    private fun buildDateSort(): SortOptions =
        SortOptions.Builder().field { f -> f.field("incidentDate").order(SortOrder.Desc) }.build()

    private fun buildDistanceSort(proximity: ProximityFilter): SortOptions =
        SortOptions.Builder().geoDistance { geo ->
            geo.field("geoPoints.location")
                .location { loc -> loc.latlon { ll -> ll.lat(proximity.latitude).lon(proximity.longitude) } }
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Kilometers)
                .distanceType(GeoDistanceType.Arc)
                .nested { n -> n.path("geoPoints") }
        }.build()

    // ========== Utility ==========

    private fun byteArrayToFloats(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return List(bytes.size / 4) { buffer.getFloat() }
    }
}
