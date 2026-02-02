package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.DebeziumOutboxEnvelope
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.toAnalysisResult
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
        val analysisResults = records.mapNotNull { record -> parseRecord(record) }
        if (analysisResults.isEmpty()) return

        runBlocking {
            runCatching { articleIndexingService.indexAll(analysisResults) }
                .onFailure { e -> log.error("Batch indexing failed: {}", e.message, e) }
        }
    }

    private fun parseRecord(record: ConsumerRecord<String, String>): AnalysisResult? {
        val rawValue = record.value() ?: run {
            log.debug("Ignoring tombstone record, offset={}", record.offset())
            return null
        }

        return try {
            val envelope = objectMapper.readValue(rawValue, DebeziumOutboxEnvelope::class.java)
            if (envelope.op !in CREATE_OPS) {
                log.debug("Ignoring non-create CDC event, offset={}", record.offset())
                return null
            }

            val after = envelope.after
            if (after == null) {
                log.warn("Received create event with null 'after' payload, offset={}", record.offset())
                return null
            }

            after.toAnalysisResult(objectMapper).also {
                log.info("Parsed analysis result CDC event: articleId={}", it.articleId)
            }
        } catch (e: Exception) {
            log.error("Failed to parse record at offset={}: {}", record.offset(), e.message, e)
            null
        }
    }
}
