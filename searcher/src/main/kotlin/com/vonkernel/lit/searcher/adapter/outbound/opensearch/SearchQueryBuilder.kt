package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.vonkernel.lit.searcher.domain.model.ProximityFilter
import com.vonkernel.lit.searcher.domain.model.RegionFilter
import com.vonkernel.lit.searcher.domain.model.SearchCriteria
import com.vonkernel.lit.searcher.domain.model.SortType
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.SortOptions
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch._types.DistanceUnit
import org.opensearch.client.opensearch._types.GeoDistanceType
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.json.JsonData
import org.opensearch.client.opensearch.core.SearchRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.format.DateTimeFormatter

object SearchQueryBuilder {

    fun build(criteria: SearchCriteria, queryEmbedding: ByteArray?, indexName: String): SearchRequest {
        val boolQuery = BoolQuery.Builder()

        buildTextQuery(criteria, boolQuery)
        buildSemanticQuery(queryEmbedding, criteria.size, boolQuery)
        buildJurisdictionCodeFilter(criteria.jurisdictionCode, boolQuery)
        buildAddressQueryFilter(criteria.addressQuery, boolQuery)
        buildRegionFilter(criteria.region, boolQuery)
        buildProximityFilter(criteria.proximity, boolQuery)
        buildIncidentTypesFilter(criteria.incidentTypes, boolQuery)
        buildUrgencyFilter(criteria.urgencyLevel, boolQuery)
        buildDateRangeFilter(criteria, boolQuery)

        val finalQuery = boolQuery.build()
        val isEmptyQuery = finalQuery.must().isEmpty()
                && finalQuery.filter().isEmpty()
                && finalQuery.should().isEmpty()

        return SearchRequest.Builder()
            .index(indexName)
            .query { q ->
                if (isEmptyQuery) q.matchAll { it }
                else q.bool(finalQuery)
            }
            .from(criteria.page * criteria.size)
            .size(criteria.size)
            .sort(buildSort(criteria))
            .highlight { h ->
                h.fields("title") { it }
                    .fields("content") { it }
                    .fields("keywords") { it }
            }
            .build()
    }

    private fun buildTextQuery(criteria: SearchCriteria, boolQuery: BoolQuery.Builder) {
        val query = criteria.query ?: return

        val multiMatch = Query.Builder().multiMatch { mm ->
            mm.query(query)
                .fields("title^3", "keywords^2", "content")
        }.build()

        when (criteria.sortBy) {
            SortType.RELEVANCE -> boolQuery.must(multiMatch)
            SortType.DATE, SortType.DISTANCE -> boolQuery.filter(multiMatch)
        }
    }

    private fun buildSemanticQuery(queryEmbedding: ByteArray?, k: Int, boolQuery: BoolQuery.Builder) {
        if (queryEmbedding == null) return

        val vector = byteArrayToFloats(queryEmbedding)

        boolQuery.should(Query.Builder().knn { knn ->
            knn.field("contentEmbedding")
                .vector(vector)
                .k(k)
        }.build())
    }

    private fun buildJurisdictionCodeFilter(jurisdictionCode: String?, boolQuery: BoolQuery.Builder) {
        if (jurisdictionCode == null) return

        boolQuery.filter(Query.Builder().prefix { p ->
            p.field("jurisdictionCodes").value(jurisdictionCode)
        }.build())
    }

    private fun buildAddressQueryFilter(addressQuery: String?, boolQuery: BoolQuery.Builder) {
        if (addressQuery == null) return

        boolQuery.filter(Query.Builder().nested { nested ->
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
        }.build())
    }

    private fun buildRegionFilter(region: RegionFilter?, boolQuery: BoolQuery.Builder) {
        if (region == null) return

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

        if (mustClauses.isEmpty()) return

        boolQuery.filter(Query.Builder().nested { nested ->
            nested.path("addresses").query { q ->
                q.bool { b -> b.must(mustClauses) }
            }
        }.build())
    }

    private fun buildProximityFilter(proximity: ProximityFilter?, boolQuery: BoolQuery.Builder) {
        if (proximity == null) return

        boolQuery.filter(Query.Builder().nested { nested ->
            nested.path("geoPoints").query { q ->
                q.geoDistance { geo ->
                    geo.field("geoPoints.location")
                        .location { loc -> loc.latlon { ll -> ll.lat(proximity.latitude).lon(proximity.longitude) } }
                        .distance("${proximity.distanceKm}km")
                }
            }
        }.build())
    }

    private fun buildIncidentTypesFilter(incidentTypes: Set<String>?, boolQuery: BoolQuery.Builder) {
        if (incidentTypes.isNullOrEmpty()) return

        boolQuery.filter(Query.Builder().nested { nested ->
            nested.path("incidentTypes").query { q ->
                q.terms { t ->
                    t.field("incidentTypes.code")
                        .terms { tv -> tv.value(incidentTypes.map { FieldValue.of(it) }) }
                }
            }
        }.build())
    }

    private fun buildUrgencyFilter(urgencyLevel: Int?, boolQuery: BoolQuery.Builder) {
        if (urgencyLevel == null) return

        boolQuery.filter(Query.Builder().range { r ->
            r.field("urgency.level").gte(JsonData.of(urgencyLevel))
        }.build())
    }

    private fun buildDateRangeFilter(criteria: SearchCriteria, boolQuery: BoolQuery.Builder) {
        if (criteria.dateFrom == null && criteria.dateTo == null) return

        boolQuery.filter(Query.Builder().range { r ->
            r.field("incidentDate").apply {
                criteria.dateFrom?.let { gte(JsonData.of(it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) }
                criteria.dateTo?.let { lte(JsonData.of(it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) }
            }
        }.build())
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
