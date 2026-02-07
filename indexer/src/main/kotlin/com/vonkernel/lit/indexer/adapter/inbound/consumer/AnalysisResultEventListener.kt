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
import java.time.Instant

@Component
class AnalysisResultEventListener(
    private val articleIndexingService: ArticleIndexingService,
    private val dlqPublisher: DlqPublisher,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class ParsedRecord(
        val rawRecord: ConsumerRecord<String, String>,
        val analysisResult: AnalysisResult,
        val analyzedAt: Instant?,
    )

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
            ?.let { runBlocking { indexBatchOrFallbackToDlq(it) } }
    }

    private suspend fun indexBatchOrFallbackToDlq(parsedRecords: List<ParsedRecord>) {
        runCatching {
            articleIndexingService.indexAll(
                parsedRecords.map { it.analysisResult },
                parsedRecords.map { it.analyzedAt }
            )
        }.onFailure { e ->
            log.error("Batch indexing failed: {}", e.message, e)
            parsedRecords.forEach { publishToDlq(it.rawRecord, it.analysisResult) }
        }
    }

    private fun parseRecord(record: ConsumerRecord<String, String>): ParsedRecord? =
        deserializeOrNull(record)
            ?.takeIf { it.op in CREATE_OPS }
            ?.after
            ?.let { it.toAnalysisResult(objectMapper) }
            ?.let { (analyzedAt, result) -> ParsedRecord(record, result, analyzedAt) }

    private fun deserializeOrNull(record: ConsumerRecord<String, String>): DebeziumOutboxEnvelope? =
        record.value()?.let {
            runCatching { objectMapper.readValue(it, DebeziumOutboxEnvelope::class.java) }
                .onFailure { e -> log.error("Failed to parse record at offset={}: {}", record.offset(), e.message, e) }
                .getOrNull()
        }

    private suspend fun publishToDlq(record: ConsumerRecord<String, String>, analysisResult: AnalysisResult) {
        runCatching { dlqPublisher.publish(record.value(), 0, analysisResult.articleId) }
            .onFailure { e ->
                log.error("Failed to publish to DLQ: articleId={}, offset={}: {}",
                    analysisResult.articleId, record.offset(), e.message, e)
            }
    }
}
