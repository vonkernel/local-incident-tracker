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

    @KafkaListener(
        topics = ["\${kafka.topic.article-events-dlq}"],
        groupId = "\${kafka.dlq.consumer.group-id}",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    fun onDlqEvent(record: ConsumerRecord<String, String>) {
        val retryCount = extractRetryCount(record)
        val rawValue = record.value()

        if (retryCount >= maxRetries) {
            log.warn("DLQ message exceeded max retries ({}), discarding: offset={}", maxRetries, record.offset())
            return
        }

        runBlocking {
            processRecord(rawValue, retryCount, record.offset())
        }
    }

    private suspend fun processRecord(rawValue: String, retryCount: Int, offset: Long) {
        var articleId: String? = null

        runCatching {
            val envelope = objectMapper.readValue(rawValue, DebeziumEnvelope::class.java)
                .takeIf { it.op in CREATE_OPS }
                ?: run {
                    log.debug("DLQ: Ignoring non-create CDC event, offset={}", offset)
                    return
                }

            val payload = envelope.after ?: run {
                log.warn("DLQ: Create event with null 'after' payload, offset={}", offset)
                return
            }

            articleId = payload.articleId
            val article = payload.toArticle()
            val eventUpdatedAt = payload.updatedAt?.let { Instant.parse(it) }

            if (isOutdated(articleId, eventUpdatedAt)) {
                log.info("DLQ: Discarding outdated event for articleId={}, offset={}", articleId, offset)
                return
            }

            articleAnalysisService.analyze(article, eventUpdatedAt)
            log.info("DLQ: Successfully reprocessed articleId={}, retryCount={}", articleId, retryCount)
        }.onFailure { e ->
            log.error("DLQ: Failed to reprocess event, offset={}, retryCount={}: {}", offset, retryCount, e.message, e)
            runCatching {
                dlqPublisher.publish(rawValue, retryCount + 1, articleId)
            }.onFailure { publishError ->
                log.error("DLQ: Failed to re-publish to DLQ, offset={}: {}", offset, publishError.message, publishError)
            }
        }
    }

    private fun isOutdated(articleId: String, eventUpdatedAt: Instant?): Boolean {
        if (eventUpdatedAt == null) return false

        val existingUpdatedAt = analysisResultRepository.findArticleUpdatedAtByArticleId(articleId)
            ?: return false

        return eventUpdatedAt.isBefore(existingUpdatedAt)
    }

    private fun extractRetryCount(record: ConsumerRecord<String, String>): Int =
        record.headers().lastHeader(RETRY_COUNT_HEADER)
            ?.value()
            ?.let { String(it).toIntOrNull() }
            ?: 0
}
