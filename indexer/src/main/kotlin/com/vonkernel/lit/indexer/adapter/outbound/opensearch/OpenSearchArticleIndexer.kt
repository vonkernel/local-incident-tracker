package com.vonkernel.lit.indexer.adapter.outbound.opensearch

import com.vonkernel.lit.core.entity.ArticleIndexDocument
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.IndexRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class OpenSearchArticleIndexer(
    private val openSearchClient: OpenSearchClient,
    @param:Value("\${opensearch.index-name:articles}") private val indexName: String,
) : ArticleIndexer {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun index(document: ArticleIndexDocument) {
        val documentMap = toMap(document)

        val request = IndexRequest.Builder<Map<String, Any?>>()
            .index(indexName)
            .id(document.articleId)
            .document(documentMap)
            .build()

        val response = openSearchClient.index(request)
        log.info("Indexed article {} with result: {}", document.articleId, response.result())
    }

    override suspend fun indexAll(documents: List<ArticleIndexDocument>) {
        if (documents.isEmpty()) return

        val bulkRequest = BulkRequest.Builder()
        documents.forEach { doc ->
            bulkRequest.operations { op ->
                op.index<Map<String, Any?>> { idx ->
                    idx.index(indexName).id(doc.articleId).document(toMap(doc))
                }
            }
        }

        val response = openSearchClient.bulk(bulkRequest.build())
        if (response.errors()) {
            response.items().filter { it.error() != null }.forEach { item ->
                log.error(
                    "Failed to bulk index document {}: {}",
                    item.id(), item.error()?.reason()
                )
            }
        }
        log.info("Bulk indexed {} documents, errors: {}", documents.size, response.errors())
    }

    override suspend fun delete(articleId: String) {
        val response = openSearchClient.delete { it.index(indexName).id(articleId) }
        log.info("Deleted article {} with result: {}", articleId, response.result())
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

    private fun ByteArray.toFloatArray(): List<Float> {
        val buffer = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.BIG_ENDIAN)
        return List(this.size / 4) { buffer.getFloat() }
    }
}
