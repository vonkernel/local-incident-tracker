package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.DebeziumOutboxEnvelope
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.toAnalysisResult
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.pow

@Component
class DlqEventListener(
    private val articleIndexingService: ArticleIndexingService,
    private val dlqPublisher: DlqPublisher,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper,
    @param:Value("\${kafka.dlq.max-retries}") private val maxRetries: Int,
    @param:Value("\${kafka.dlq.base-delay-ms:2000}") private val baseDelayMs: Long = 2000L,
    @param:Value("\${kafka.dlq.backoff-factor:2.0}") private val backoffFactor: Double = 2.0,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class ParsedEnvelope(
        val articleId: String,
        val analysisResult: AnalysisResult,
        val analyzedAt: Instant?,
    )

    companion object {
        private const val RETRY_COUNT_HEADER = "dlq-retry-count"
        private val CREATE_OPS = setOf("c", "r")
    }

    @KafkaListener(
        topics = ["\${kafka.topic.analysis-events-dlq}"],
        groupId = "\${kafka.dlq.consumer.group-id}",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    fun onDlqEvent(record: ConsumerRecord<String, String>) {
        extractRetryCount(record)
            .takeIf { it < maxRetries }
            ?.let { retryCount -> runBlocking { processRecord(record, retryCount) } }
            ?: log.warn("DLQ message exceeded max retries ({}), discarding: offset={}", maxRetries, record.offset())
    }

    private suspend fun processRecord(record: ConsumerRecord<String, String>, retryCount: Int) {
        if (retryCount > 0) {
            val delayMs = calculateBackoffDelay(retryCount)
            log.info("DLQ: Applying backoff delay of {}ms for retryCount={}", delayMs, retryCount)
            delay(delayMs)
        }

        parseEnvelope(record)?.let { parsed ->
            runCatching { articleIndexingService.index(parsed.analysisResult, parsed.analyzedAt) }
                .onSuccess { log.info("DLQ: Successfully re-indexed articleId={}, retryCount={}", parsed.articleId, retryCount) }
                .onFailure { e -> handleReindexFailure(record, retryCount, parsed.articleId, e) }
        }
    }

    private fun calculateBackoffDelay(retryCount: Int): Long =
        (baseDelayMs * backoffFactor.pow(retryCount - 1)).toLong()

    private suspend fun handleReindexFailure(record: ConsumerRecord<String, String>, retryCount: Int, articleId: String, e: Throwable) {
        log.error("DLQ: Failed to re-index event, offset={}, retryCount={}: {}", record.offset(), retryCount, e.message, e)
        republishToDlqOrThrow(record, retryCount + 1, articleId)
    }

    private fun parseEnvelope(record: ConsumerRecord<String, String>): ParsedEnvelope? =
        deserializeOrNull(record)
            ?.takeIf { it.op in CREATE_OPS }
            ?.after
            ?.let { it.toAnalysisResult(objectMapper) }
            ?.let { (analyzedAt, result) -> ParsedEnvelope(result.articleId, result, analyzedAt) }

    private fun deserializeOrNull(record: ConsumerRecord<String, String>): DebeziumOutboxEnvelope? =
        record.value()?.let {
            runCatching { objectMapper.readValue(it, DebeziumOutboxEnvelope::class.java) }
                .onFailure { e -> log.error("DLQ: Failed to parse envelope at offset={}: {}", record.offset(), e.message, e) }
                .getOrNull()
        }

    private suspend fun republishToDlqOrThrow(record: ConsumerRecord<String, String>, retryCount: Int, articleId: String) {
        runCatching { dlqPublisher.publish(record.value(), retryCount, articleId) }
            .onFailure { e -> log.error("DLQ: Failed to re-publish to DLQ, offset={}: {}", record.offset(), e.message, e) }
            .getOrThrow()
    }

    private fun extractRetryCount(record: ConsumerRecord<String, String>): Int =
        record.headers().lastHeader(RETRY_COUNT_HEADER)
            ?.value()
            ?.let { String(it).toIntOrNull() }
            ?: 0
}
