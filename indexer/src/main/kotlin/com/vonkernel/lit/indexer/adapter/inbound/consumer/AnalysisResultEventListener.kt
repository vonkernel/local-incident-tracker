package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.DebeziumOutboxEnvelope
import com.vonkernel.lit.indexer.adapter.inbound.consumer.model.toAnalysisResult
import com.vonkernel.lit.indexer.domain.exception.BulkIndexingPartialFailureException
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
        runBlocking {
            records
                .mapNotNull { parseRecord(it) }
                .takeIf { it.isNotEmpty() }
                ?.let { indexBatchOrFallbackToDlq(it) }
        }
    }

    // 배치 인덱싱 시도 → 실패 시 개별 레코드 폴백
    private suspend fun indexBatchOrFallbackToDlq(parsedRecords: List<ParsedRecord>) {
        runCatching { indexAllOrThrow(parsedRecords) }
            .onFailure { e -> fallbackToPerRecordIndexing(parsedRecords, e) }
    }

    // 복구 불가능한 부수효과: 배치 인덱싱
    private suspend fun indexAllOrThrow(parsedRecords: List<ParsedRecord>) {
        articleIndexingService.indexAll(
            parsedRecords.map { it.analysisResult },
            parsedRecords.map { it.analyzedAt }
        )
    }

    // 복구 불가능한 부수효과: 배치 실패 시 개별 레코드 폴백
    private suspend fun fallbackToPerRecordIndexing(parsedRecords: List<ParsedRecord>, e: Throwable) {
        log.error("Batch indexing failed, falling back to per-record indexing: {}", e.message, e)
        selectRecordsToRetry(parsedRecords, e)
            .forEach { indexOneOrPublishToDlq(it) }
    }

    // 순수 함수: 실패 유형에 따라 재시도 대상 선별
    private fun selectRecordsToRetry(parsedRecords: List<ParsedRecord>, e: Throwable): List<ParsedRecord> =
        when (e) {
            is BulkIndexingPartialFailureException ->
                parsedRecords.filter { it.analysisResult.articleId in e.failedArticleIds.toSet() }
            else -> parsedRecords
        }

    // 개별 인덱싱 시도 → 실패 시 DLQ 발행
    private suspend fun indexOneOrPublishToDlq(parsed: ParsedRecord) {
        runCatching { articleIndexingService.index(parsed.analysisResult, parsed.analyzedAt) }
            .onSuccess { log.info("Per-record fallback succeeded for articleId={}", parsed.analysisResult.articleId) }
            .onFailure { e -> handleIndexFailureOrThrow(parsed, e) }
    }

    // 복구 불가능한 부수효과: 인덱싱 실패 로깅 후 DLQ 발행
    private suspend fun handleIndexFailureOrThrow(parsed: ParsedRecord, e: Throwable) {
        log.error("Per-record fallback failed for articleId={}: {}", parsed.analysisResult.articleId, e.message, e)
        publishToDlqOrThrow(parsed.rawRecord, parsed.analysisResult)
    }

    private suspend fun parseRecord(record: ConsumerRecord<String, String>): ParsedRecord? =
        deserializeOrPublishToDlq(record)
            ?.takeIf { it.op in CREATE_OPS }
            ?.after
            ?.let { it.toAnalysisResult(objectMapper) }
            ?.let { (analyzedAt, result) -> ParsedRecord(record, result, analyzedAt) }

    private suspend fun deserializeOrPublishToDlq(record: ConsumerRecord<String, String>): DebeziumOutboxEnvelope? =
        record.value()?.let { value ->
            runCatching { objectMapper.readValue(value, DebeziumOutboxEnvelope::class.java) }
                .onFailure { e ->
                    log.error("Failed to parse record at offset={}: {}", record.offset(), e.message, e)
                    publishRawToDlqOrThrow(value, record.offset())
                }
                .getOrNull()
        }

    private suspend fun publishRawToDlqOrThrow(rawValue: String, offset: Long) {
        runCatching { dlqPublisher.publish(rawValue, 0, null) }
            .onFailure { e -> log.error("Failed to publish unparseable record to DLQ at offset={}: {}", offset, e.message, e) }
            .getOrThrow()
    }

    private suspend fun publishToDlqOrThrow(record: ConsumerRecord<String, String>, analysisResult: AnalysisResult) {
        runCatching { dlqPublisher.publish(record.value(), 0, analysisResult.articleId) }
            .onFailure { e ->
                log.error("Failed to publish to DLQ: articleId={}, offset={}: {}",
                    analysisResult.articleId, record.offset(), e.message, e)
            }
            .getOrThrow()
    }
}
