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
        // 1. 필터 구성 (AND 조건)
        val filterQueries = buildFilters(criteria)

        // 2. 텍스트 쿼리 구성 (multiMatch OR kNN)
        //    semantic 검색 시 필터를 sub-query 내부에 pre-filter로 적용
        val textQuery = buildTextQuery(criteria, queryEmbedding, filterQueries)

        // 3. 최종 쿼리 조합
        //    semantic 활성화 시 필터가 이미 sub-query 내부에 적용됨 (pre-filter)
        val isHybrid = queryEmbedding != null && textQuery != null

        val topLevelQuery = if (textQuery != null && filterQueries.isNotEmpty() && !isHybrid) {
            Query.Builder().bool { b ->
                b.must(textQuery).filter(filterQueries)
            }.build()
        } else textQuery
            ?: if (filterQueries.isNotEmpty()) {
                Query.Builder().bool { b -> b.filter(filterQueries) }.build()
            } else {
                Query.Builder().matchAll { it }.build()
            }

        val requestBuilder = SearchRequest.Builder()
            .index(indexName)
            .query(topLevelQuery)
            .from(criteria.page * criteria.size)
            .size(criteria.size)
            .sort(buildSort(criteria))
            .highlight { h ->
                h.fields("title") { it }
                    .fields("content") { it }
                    .fields("keywords") { it }
            }

        // hybrid 쿼리 사용 시 점수 정규화 pipeline 적용
        if (isHybrid) {
            requestBuilder.pipeline(HYBRID_SEARCH_PIPELINE)
        }

        return requestBuilder.build()
    }

    /**
     * 텍스트 쿼리 구성.
     * - non-semantic: multiMatch(AND, best_fields)
     * - semantic: hybrid { multiMatch(AND, boost=0.2), knn(minScore, boost=10.0) }
     * - query 없음: null
     */
    private fun buildTextQuery(
        criteria: SearchCriteria,
        queryEmbedding: ByteArray?,
        filterQueries: List<Query>,
    ): Query? {
        val query = criteria.query
        val hasText = !query.isNullOrBlank()
        val hasSemantic = queryEmbedding != null

        if (!hasText) return null

        val multiMatchQuery = Query.Builder().multiMatch { mm ->
            mm.query(query)
                .fields("title^3", "keywords^2", "content")
                .operator(Operator.And)
                .type(TextQueryType.BestFields)
        }.build()

        // kNN에 pre-filter 적용: 필터 조건 만족하는 문서 집합 내에서만 벡터 검색
        val knnQuery = if (hasSemantic) {
            val vector = byteArrayToFloats(queryEmbedding)
            val preFilter = if (filterQueries.isNotEmpty()) {
                Query.Builder().bool { b -> b.filter(filterQueries) }.build()
            } else null
            Query.Builder().knn { knn ->
                knn.field("contentEmbedding")
                    .vector(vector)
                    .minScore(KNN_MIN_SCORE)
                preFilter?.let { knn.filter(it) }
                knn
            }.build()
        } else null

        // semantic + text → hybrid (multiMatch OR kNN, 각자 점수 기여)
        // BM25 sub-query에도 pre-filter 적용
        if (knnQuery != null) {
            val bm25Boosted = Query.Builder().bool { b ->
                b.must(multiMatchQuery).boost(BM25_BOOST_WITH_SEMANTIC)
                if (filterQueries.isNotEmpty()) b.filter(filterQueries)
                b
            }.build()
            val knnBoosted = Query.Builder().bool { b ->
                b.must(knnQuery).boost(KNN_BOOST)
            }.build()
            return Query.Builder().hybrid { h ->
                h.queries(bm25Boosted, knnBoosted)
            }.build()
        }

        // text only (필터는 caller에서 적용, BM25에 대해 Lucene이 pre-filter 처리)
        return multiMatchQuery
    }

    private fun buildFilters(criteria: SearchCriteria): List<Query> = buildList {
        buildJurisdictionCodeFilter(criteria.jurisdictionCode)?.let(::add)
        buildAddressQueryFilter(criteria.addressQuery)?.let(::add)
        buildRegionFilter(criteria.region)?.let(::add)
        buildProximityFilter(criteria.proximity)?.let(::add)
        buildIncidentTypesFilter(criteria.incidentTypes)?.let(::add)
        buildUrgencyFilter(criteria.urgencyLevel)?.let(::add)
        buildDateRangeFilter(criteria)?.let(::add)
    }

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

        val mustClauses = buildList {
            region.depth1Name?.let { name ->
                add(Query.Builder().term { t ->
                    t.field("addresses.depth1Name").value(FieldValue.of(name))
                }.build())
            }
            region.depth2Name?.let { name ->
                add(Query.Builder().term { t ->
                    t.field("addresses.depth2Name").value(FieldValue.of(name))
                }.build())
            }
            region.depth3Name?.let { name ->
                add(Query.Builder().term { t ->
                    t.field("addresses.depth3Name").value(FieldValue.of(name))
                }.build())
            }
        }

        if (mustClauses.isEmpty()) return null

        return Query.Builder().nested { nested ->
            nested.path("addresses").query { q ->
                q.bool { b -> b.must(mustClauses) }
            }
        }.build()
    }

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

    private fun buildSort(criteria: SearchCriteria): List<SortOptions> = when (criteria.sortBy) {
        SortType.RELEVANCE -> listOf(
            SortOptions.Builder().score { it.order(SortOrder.Desc) }.build()
        )
        SortType.DATE -> listOf(
            SortOptions.Builder().field { f ->
                f.field("incidentDate").order(SortOrder.Desc)
            }.build()
        )
        SortType.DISTANCE -> {
            val proximity = criteria.proximity!!
            listOf(
                SortOptions.Builder().geoDistance { geo ->
                    geo.field("geoPoints.location")
                        .location { loc -> loc.latlon { ll -> ll.lat(proximity.latitude).lon(proximity.longitude) } }
                        .order(SortOrder.Asc)
                        .unit(DistanceUnit.Kilometers)
                        .distanceType(GeoDistanceType.Arc)
                        .nested { n -> n.path("geoPoints") }
                }.build()
            )
        }
    }

    private fun byteArrayToFloats(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return List(bytes.size / 4) { buffer.getFloat() }
    }
}
