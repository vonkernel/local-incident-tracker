package com.vonkernel.lit.analyzer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.toArticle
import com.vonkernel.lit.analyzer.domain.port.DlqPublisher
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import com.vonkernel.lit.core.port.repository.AnalysisResultRepository
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DlqEventListener(
    private val articleAnalysisService: ArticleAnalysisService,
    private val analysisResultRepository: AnalysisResultRepository,
    private val dlqPublisher: DlqPublisher,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper,
    @param:Value("\${kafka.dlq.max-retries}") private val maxRetries: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val RETRY_COUNT_HEADER = "dlq-retry-count"
        private val CREATE_OPS = setOf("c", "r")
    }

    private data class ParsedDlqEvent(
        val articleId: String,
        val article: com.vonkernel.lit.core.entity.Article,
        val articleUpdatedAt: Instant?,
    )

    @KafkaListener(
        topics = ["\${kafka.topic.article-events-dlq}"],
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
        parseRecordOrNull(record)
            ?.takeUnless { isOutdated(it.articleId, it.articleUpdatedAt, record.offset()) }
            ?.let { analyzeOrRepublish(record, it, retryCount) }
    }

    private suspend fun analyzeOrRepublish(
        record: ConsumerRecord<String, String>, parsed: ParsedDlqEvent, retryCount: Int
    ) {
        runCatching { articleAnalysisService.analyze(parsed.article, parsed.articleUpdatedAt) }
            .onSuccess { log.info("DLQ: Successfully reprocessed articleId={}, retryCount={}", parsed.articleId, retryCount) }
            .onFailure { e ->
                log.error("DLQ: Failed to reprocess event, offset={}, retryCount={}: {}", record.offset(), retryCount, e.message, e)
                republishToDlqSafely(record, retryCount + 1, parsed.articleId)
            }
    }

    // ========== Parsing ==========

    private fun parseRecordOrNull(record: ConsumerRecord<String, String>): ParsedDlqEvent? =
        deserializeOrNull(record)
            ?.takeIf { it.op in CREATE_OPS }
            ?.after
            ?.let { payload ->
                ParsedDlqEvent(
                    articleId = payload.articleId,
                    article = payload.toArticle(),
                    articleUpdatedAt = payload.updatedAt?.let { Instant.parse(it) }
                )
            }

    private fun deserializeOrNull(record: ConsumerRecord<String, String>): DebeziumEnvelope? =
        record.value()?.let {
            runCatching { objectMapper.readValue(it, DebeziumEnvelope::class.java) }
                .onFailure { e -> log.error("DLQ: Failed to parse record at offset={}: {}", record.offset(), e.message, e) }
                .getOrNull()
        }

    // ========== Side Effects ==========

    private fun isOutdated(articleId: String, eventUpdatedAt: Instant?, offset: Long): Boolean =
        eventUpdatedAt
            ?.let { analysisResultRepository.findArticleUpdatedAtByArticleId(articleId) }
            ?.let { existing -> eventUpdatedAt.isBefore(existing) }
            ?.also { outdated -> if (outdated) log.info("DLQ: Discarding outdated event for articleId={}, offset={}", articleId, offset) }
            ?: false

    private suspend fun republishToDlqSafely(record: ConsumerRecord<String, String>, retryCount: Int, articleId: String) {
        runCatching { dlqPublisher.publish(record.value(), retryCount, articleId) }
            .onFailure { e -> log.error("DLQ: Failed to re-publish to DLQ, offset={}: {}", record.offset(), e.message, e) }
    }

    private fun extractRetryCount(record: ConsumerRecord<String, String>): Int =
        record.headers().lastHeader(RETRY_COUNT_HEADER)
            ?.value()
            ?.let { String(it).toIntOrNull() }
            ?: 0
}
