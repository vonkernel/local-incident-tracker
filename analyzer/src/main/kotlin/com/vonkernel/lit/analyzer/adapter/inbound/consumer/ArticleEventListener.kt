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

    private suspend fun processRecord(record: ConsumerRecord<String, String>) {
        var articleId: String? = null

        runCatching {
            val envelope = objectMapper.readValue(record.value(), DebeziumEnvelope::class.java)
                .takeIf { it.op in CREATE_OPS }

            val payload = envelope?.after
                ?.also {
                    articleId = it.articleId
                    log.info("Received article CDC event: articleId={}", it.articleId)
                }
                ?: run {
                    if (envelope != null) {
                        log.warn("Received create event with null 'after' payload, offset={}", record.offset())
                    } else {
                        log.debug("Ignoring non-create CDC event, offset={}", record.offset())
                    }
                    return
                }

            val article = payload.toArticle()
            val articleUpdatedAt = payload.updatedAt?.let { Instant.parse(it) }

            articleAnalysisService.analyze(article, articleUpdatedAt)
        }.onFailure { e ->
            when (e) {
                is ArticleAnalysisException ->
                    log.error("Failed to analyze article {} at offset={}: {}", e.articleId, record.offset(), e.message, e)
                else ->
                    log.error("Failed to process article event at offset={}: {}", record.offset(), e.message, e)
            }

            runCatching {
                dlqPublisher.publish(record.value(), 0, articleId)
            }.onFailure { dlqError ->
                log.error("Failed to publish to DLQ at offset={}: {}", record.offset(), dlqError.message, dlqError)
            }
        }
    }
}
