package com.vonkernel.lit.analyzer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.toArticle
import com.vonkernel.lit.analyzer.domain.exception.ArticleAnalysisException
import com.vonkernel.lit.analyzer.domain.port.DlqPublisher
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ArticleEventListener(
    private val articleAnalysisService: ArticleAnalysisService,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper,
    private val dlqPublisher: DlqPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val CREATE_OPS = setOf("c", "r")
    }

    @KafkaListener(
        topics = ["\${kafka.topic.article-events}"],
        groupId = "\${kafka.consumer.group-id}"
    )
    fun onArticleEvents(records: List<ConsumerRecord<String, String>>) {
        log.info("Received batch of {} records", records.size)
        runBlocking {
            supervisorScope {
                records
                    .map { record -> async { processRecord(record) } }
                    .awaitAll()
            }
        }
    }

    private data class ParsedArticleEvent(
        val articleId: String,
        val article: com.vonkernel.lit.core.entity.Article,
        val articleUpdatedAt: Instant?,
    )

    private suspend fun processRecord(record: ConsumerRecord<String, String>) {
        parseRecordOrNull(record)?.let { parsed ->
            runCatching { articleAnalysisService.analyze(parsed.article, parsed.articleUpdatedAt) }
                .onFailure { e ->
                    logAnalysisFailure(e, parsed.articleId, record.offset())
                    publishToDlqSafely(record.value(), parsed.articleId, record.offset())
                }
        }
    }

    private suspend fun parseRecordOrNull(record: ConsumerRecord<String, String>): ParsedArticleEvent? =
        deserializeOrNull(record)
            ?.takeIf { it.op in CREATE_OPS }
            ?.after
            ?.let { payload ->
                ParsedArticleEvent(
                    articleId = payload.articleId,
                    article = payload.toArticle(),
                    articleUpdatedAt = payload.updatedAt?.let { Instant.parse(it) }
                )
            }

    private suspend fun deserializeOrNull(record: ConsumerRecord<String, String>): DebeziumEnvelope? =
        runCatching { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) }
            .onFailure { e ->
                log.error("Failed to parse record at offset={}: {}", record.offset(), e.message, e)
                publishToDlqSafely(record.value(), null, record.offset())
            }
            .getOrNull()

    private fun logAnalysisFailure(e: Throwable, articleId: String, offset: Long) {
        when (e) {
            is ArticleAnalysisException ->
                log.error("Failed to analyze article {} at offset={}: {}", e.articleId, offset, e.message, e)
            else ->
                log.error("Failed to process article event at offset={}: {}", offset, e.message, e)
        }
    }

    private suspend fun publishToDlqSafely(value: String, articleId: String?, offset: Long) {
        runCatching { dlqPublisher.publish(value, 0, articleId) }
            .onFailure { e -> log.error("Failed to publish to DLQ at offset={}: {}", offset, e.message, e) }
    }
}
