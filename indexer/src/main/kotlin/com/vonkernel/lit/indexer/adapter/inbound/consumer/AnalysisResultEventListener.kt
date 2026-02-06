package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.DebeziumOutboxEnvelope
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.toAnalysisResult
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class AnalysisResultEventListener(
    private val articleIndexingService: ArticleIndexingService,
    private val dlqPublisher: DlqPublisher,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val CREATE_OPS = setOf("c", "r")
    }

    @KafkaListener(
        topics = ["\${kafka.topic.analysis-events}"],
        groupId = "\${kafka.consumer.group-id}"
    )
    fun onAnalysisResultEvents(records: List<ConsumerRecord<String, String>>) {
        log.info("Received batch of {} records", records.size)

        records
            .mapNotNull { parseRecord(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { parsedRecords ->
                runBlocking {
                    runCatching { articleIndexingService.indexAll(parsedRecords.map { it.second }) }
                        .onFailure { e ->
                            log.error("Batch indexing failed: {}", e.message, e)
                            parsedRecords.forEach { (record, result) -> publishToDlq(record, result) }
                        }
                }
            }
    }

    private fun parseRecord(record: ConsumerRecord<String, String>): Pair<ConsumerRecord<String, String>, AnalysisResult>? =
        record.value()
            ?.let { rawValue ->
                runCatching { objectMapper.readValue(rawValue, DebeziumOutboxEnvelope::class.java) }
                    .onFailure { e -> log.error("Failed to parse record at offset={}: {}", record.offset(), e.message, e) }
                    .getOrNull()
            }
            ?.takeIf { it.op in CREATE_OPS }
            ?.also { if (it.op !in CREATE_OPS) log.debug("Ignoring non-create CDC event, offset={}", record.offset()) }
            ?.after
            ?.let { payload ->
                payload.toAnalysisResult(objectMapper)
                    .also { log.info("Parsed analysis result CDC event: articleId={}", it.articleId) }
                    .let { record to it }
            }
            ?: run {
                if (record.value() == null) log.debug("Ignoring tombstone record, offset={}", record.offset())
                null
            }

    private suspend fun publishToDlq(record: ConsumerRecord<String, String>, analysisResult: AnalysisResult) {
        runCatching { dlqPublisher.publish(record.value(), 0, analysisResult.articleId) }
            .onFailure { e ->
                log.error("Failed to publish to DLQ: articleId={}, offset={}: {}",
                    analysisResult.articleId, record.offset(), e.message, e)
            }
    }
}
