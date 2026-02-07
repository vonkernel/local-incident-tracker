package com.vonkernel.lit.indexer.adapter.outbound.opensearch

import com.vonkernel.lit.core.entity.ArticleIndexDocument
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.BulkResponse
import org.opensearch.client.opensearch.core.GetResponse
import org.opensearch.client.opensearch.core.IndexRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.format.DateTimeFormatter

@Component
class OpenSearchArticleIndexer(
    private val openSearchClient: OpenSearchClient,
    @param:Value("\${opensearch.index-name:articles}") private val indexName: String,
) : ArticleIndexer {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun index(document: ArticleIndexDocument) {
        IndexRequest.Builder<Map<String, Any?>>()
            .index(indexName)
            .id(document.articleId)
            .document(toMap(document))
            .build()
            .let { openSearchClient.index(it) }
            .also { log.info("Indexed article {} with result: {}", document.articleId, it.result()) }
    }

    override suspend fun indexAll(documents: List<ArticleIndexDocument>) {
        documents.takeIf { it.isNotEmpty() }
            ?.let { buildBulkRequest(it) }
            ?.let { openSearchClient.bulk(it) }
            ?.also { logBulkResult(it, documents.size) }
    }

    private fun buildBulkRequest(documents: List<ArticleIndexDocument>): BulkRequest =
        documents.fold(BulkRequest.Builder()) { builder, doc ->
            builder.operations { op -> op.index { it.index(indexName).id(doc.articleId).document(toMap(doc)) } }
        }.build()

    private fun logBulkResult(response: BulkResponse, count: Int) {
        response.items()
            .filter { it.error() != null }
            .forEach { log.error("Failed to bulk index document {}: {}", it.id(), it.error()?.reason()) }
        log.info("Bulk indexed {} documents, errors: {}", count, response.errors())
    }

    override suspend fun findModifiedAtByArticleId(articleId: String): Instant? =
        getDocumentOrNull(articleId)
            ?.takeIf { it.found() }
            ?.source()
            ?.get("modifiedAt")
            ?.let { Instant.parse(it as String) }

    private fun getDocumentOrNull(articleId: String): GetResponse<Map<*, *>>? =
        runCatching {
            openSearchClient.get(
                { it.index(indexName).id(articleId).sourceIncludes("modifiedAt") },
                Map::class.java
            )
        }
            .onFailure { e -> log.warn("Failed to fetch document from OpenSearch: articleId={}, error={}", articleId, e.message) }
            .getOrNull()

    override suspend fun delete(articleId: String) {
        openSearchClient.delete { it.index(indexName).id(articleId) }
            .also { log.info("Deleted article {} with result: {}", articleId, it.result()) }
    }

    private fun toMap(document: ArticleIndexDocument): Map<String, Any?> = buildMap {
        put("articleId", document.articleId)
        put("sourceId", document.sourceId)
        put("originId", document.originId)
        put("title", document.title)
        put("content", document.content)
        put("keywords", document.keywords)
        put("contentEmbedding", document.contentEmbedding?.toFloatArray())
        put("incidentTypes", document.incidentTypes?.map { mapOf("code" to it.code, "name" to it.name) })
        put("urgency", document.urgency?.let { mapOf("name" to it.name, "level" to it.level) })
        put("incidentDate", document.incidentDate?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        put("geoPoints", document.geoPoints?.map { coord ->
            mapOf(
                "lat" to coord.lat,
                "lon" to coord.lon,
                "location" to mapOf("lat" to coord.lat, "lon" to coord.lon)
            )
        })
        put("addresses", document.addresses?.map { addr ->
            mapOf(
                "regionType" to addr.regionType.name,
                "code" to addr.code,
                "addressName" to addr.addressName,
                "depth1Name" to addr.depth1Name,
                "depth2Name" to addr.depth2Name,
                "depth3Name" to addr.depth3Name,
            )
        })
        put("jurisdictionCodes", document.jurisdictionCodes?.toList())
        put("writtenAt", document.writtenAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        put("modifiedAt", document.modifiedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    private fun ByteArray.toFloatArray(): List<Float> =
        ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN)
            .let { buffer -> List(this.size / 4) { buffer.float } }
}
