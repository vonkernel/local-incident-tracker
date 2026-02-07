package com.vonkernel.lit.searcher.adapter.outbound.opensearch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vonkernel.lit.core.entity.*
import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.model.SearchResultItem
import org.opensearch.client.opensearch.core.SearchResponse
import java.time.ZonedDateTime

object SearchResultMapper {

    fun map(response: SearchResponse<ObjectNode>, page: Int, size: Int): SearchResult =
        SearchResult(
            items = response.hits().hits().map { hit ->
                SearchResultItem(
                    document = mapDocument(hit.source()!!),
                    score = hit.score()?.toFloat(),
                    highlights = hit.highlight().mapValues { (_, v) -> v },
                )
            },
            totalHits = response.hits().total()?.value() ?: 0L,
            page = page,
            size = size,
        )

    private fun mapDocument(source: ObjectNode): ArticleIndexDocument =
        ArticleIndexDocument(
            articleId = source.getText("articleId")!!,
            sourceId = source.getText("sourceId"),
            originId = source.getText("originId"),
            title = source.getText("title"),
            content = source.getText("content"),
            keywords = source.getArray("keywords")?.mapNotNull { it.asText(null) },
            incidentTypes = source.getArray("incidentTypes")?.map { mapIncidentType(it) }?.toSet(),
            urgency = source.getObject("urgency")?.let { mapUrgency(it) },
            incidentDate = source.getText("incidentDate")?.let { ZonedDateTime.parse(it) },
            geoPoints = source.getArray("geoPoints")?.map { mapCoordinate(it) },
            addresses = source.getArray("addresses")?.map { mapAddress(it) },
            jurisdictionCodes = source.getArray("jurisdictionCodes")
                ?.mapNotNull { it.asText(null) }?.toSet(),
            writtenAt = source.getText("writtenAt")?.let { ZonedDateTime.parse(it) },
            modifiedAt = source.getText("modifiedAt")?.let { ZonedDateTime.parse(it) },
        )

    private fun mapIncidentType(node: JsonNode): IncidentType =
        IncidentType(code = node.getText("code")!!, name = node.getText("name")!!)

    private fun mapUrgency(node: JsonNode): Urgency =
        Urgency(name = node.getText("name")!!, level = node["level"].asInt())

    private fun mapCoordinate(node: JsonNode): Coordinate =
        Coordinate(lat = node["lat"].asDouble(), lon = node["lon"].asDouble())

    private fun mapAddress(node: JsonNode): Address =
        Address(
            regionType = parseRegionTypeOrDefault(node.getText("regionType")),
            code = node.getText("code")!!,
            addressName = node.getText("addressName")!!,
            depth1Name = node.getText("depth1Name"),
            depth2Name = node.getText("depth2Name"),
            depth3Name = node.getText("depth3Name"),
        )

    private fun parseRegionTypeOrDefault(value: String?): RegionType =
        value
            ?.let { runCatching { RegionType.valueOf(it) }.getOrNull() }
            ?: RegionType.UNKNOWN

    private fun JsonNode.getText(key: String): String? =
        get(key)?.takeIf { !it.isNull }?.asText()

    private fun JsonNode.getObject(key: String): JsonNode? =
        get(key)?.takeIf { it.isObject }

    private fun JsonNode.getArray(key: String): List<JsonNode>? =
        get(key)?.takeIf { it.isArray }?.toList()
}
