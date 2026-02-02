package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vonkernel.lit.core.entity.*
import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.model.SearchResultItem
import org.opensearch.client.opensearch.core.SearchResponse
import java.time.ZonedDateTime

object SearchResultMapper {

    fun map(response: SearchResponse<ObjectNode>, page: Int, size: Int): SearchResult {
        val items = response.hits().hits().map { hit ->
            SearchResultItem(
                document = mapDocument(hit.source()!!),
                score = hit.score()?.toFloat(),
                highlights = hit.highlight().mapValues { (_, v) -> v },
            )
        }

        return SearchResult(
            items = items,
            totalHits = response.hits().total()?.value() ?: 0L,
            page = page,
            size = size,
        )
    }

    private fun mapDocument(source: ObjectNode): ArticleIndexDocument {
        return ArticleIndexDocument(
            articleId = source.getText("articleId")!!,
            sourceId = source.getText("sourceId"),
            originId = source.getText("originId"),
            title = source.getText("title"),
            content = source.getText("content"),
            keywords = source.getArray("keywords")?.mapNotNull { it.asText(null) },
            incidentTypes = source.getArray("incidentTypes")?.map { obj ->
                IncidentType(code = obj.getText("code")!!, name = obj.getText("name")!!)
            }?.toSet(),
            urgency = source.getObject("urgency")?.let { u ->
                Urgency(name = u.getText("name")!!, level = u["level"].asInt())
            },
            incidentDate = source.getText("incidentDate")?.let { ZonedDateTime.parse(it) },
            geoPoints = source.getArray("geoPoints")?.map { obj ->
                Coordinate(lat = obj["lat"].asDouble(), lon = obj["lon"].asDouble())
            },
            addresses = source.getArray("addresses")?.map { obj ->
                Address(
                    regionType = runCatching { RegionType.valueOf(obj.getText("regionType")!!) }
                        .getOrDefault(RegionType.UNKNOWN),
                    code = obj.getText("code")!!,
                    addressName = obj.getText("addressName")!!,
                    depth1Name = obj.getText("depth1Name"),
                    depth2Name = obj.getText("depth2Name"),
                    depth3Name = obj.getText("depth3Name"),
                )
            },
            jurisdictionCodes = source.getArray("jurisdictionCodes")
                ?.mapNotNull { it.asText(null) }?.toSet(),
            writtenAt = source.getText("writtenAt")?.let { ZonedDateTime.parse(it) },
            modifiedAt = source.getText("modifiedAt")?.let { ZonedDateTime.parse(it) },
        )
    }

    private fun JsonNode.getText(key: String): String? =
        get(key)?.takeIf { !it.isNull }?.asText()

    private fun JsonNode.getObject(key: String): JsonNode? =
        get(key)?.takeIf { it.isObject }

    private fun JsonNode.getArray(key: String): List<JsonNode>? =
        get(key)?.takeIf { it.isArray }?.toList()
}
