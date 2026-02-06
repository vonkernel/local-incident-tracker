package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.DebeziumOutboxEnvelope
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.toAnalysisResult
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DlqEventListener(
    private val articleIndexingService: ArticleIndexingService,
    private val dlqPublisher: DlqPublisher,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper,
    @param:Value("\${kafka.dlq.max-retries}") private val maxRetries: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
            ?.let { retryCount -> runBlocking { processRecord(record.value(), retryCount, record.offset()) } }
            ?: log.warn("DLQ message exceeded max retries ({}), discarding: offset={}", maxRetries, record.offset())
    }

    private suspend fun processRecord(rawValue: String, retryCount: Int, offset: Long) {
        parseEnvelope(rawValue, offset)
            ?.let { (articleId, analysisResult) ->
                runCatching { articleIndexingService.index(analysisResult) }
                    .onSuccess { log.info("DLQ: Successfully re-indexed articleId={}, retryCount={}", articleId, retryCount) }
                    .onFailure { e ->
                        log.error("DLQ: Failed to re-index event, offset={}, retryCount={}: {}", offset, retryCount, e.message, e)
                        republishToDlq(rawValue, retryCount + 1, articleId, offset)
                    }
            }
    }

    private fun parseEnvelope(rawValue: String, offset: Long) =
        runCatching { objectMapper.readValue(rawValue, DebeziumOutboxEnvelope::class.java) }
            .getOrNull()
            ?.takeIf { it.op in CREATE_OPS }
            ?.also { if (it.op !in CREATE_OPS) log.debug("DLQ: Ignoring non-create CDC event, offset={}", offset) }
            ?.after
            ?.let { payload -> payload.articleId to payload.toAnalysisResult(objectMapper) }

    private suspend fun republishToDlq(rawValue: String, retryCount: Int, articleId: String, offset: Long) {
        runCatching { dlqPublisher.publish(rawValue, retryCount, articleId) }
            .onFailure { e -> log.error("DLQ: Failed to re-publish to DLQ, offset={}: {}", offset, e.message, e) }
    }

    private fun extractRetryCount(record: ConsumerRecord<String, String>): Int =
        record.headers().lastHeader(RETRY_COUNT_HEADER)
            ?.value()
            ?.let { String(it).toIntOrNull() }
            ?: 0
}
