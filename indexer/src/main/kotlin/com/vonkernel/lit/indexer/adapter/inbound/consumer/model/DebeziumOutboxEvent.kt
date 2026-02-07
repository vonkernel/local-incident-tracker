package com.vonkernel.lit.indexer.adapter.inbound.consumer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.core.entity.AnalysisResult
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumOutboxEnvelope(
    val before: OutboxPayload?,
    val after: OutboxPayload?,
    val op: String,
    val source: DebeziumSource? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OutboxPayload(
    val id: Long?,
    @param:JsonProperty("article_id")
    val articleId: String,
    val payload: String,
    @param:JsonProperty("created_at")
    val createdAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumSource(
    val table: String? = null,
    val connector: String? = null
)

fun OutboxPayload.toAnalysisResult(objectMapper: ObjectMapper): Pair<Instant?, AnalysisResult> =
    createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } to
        objectMapper.readValue(payload, AnalysisResult::class.java)
