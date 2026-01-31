package com.vonkernel.lit.analyzer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.toArticle
import com.vonkernel.lit.analyzer.domain.exception.ArticleAnalysisException
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ArticleEventListener(
    private val articleAnalysisService: ArticleAnalysisService,
    @param:Qualifier("debeziumObjectMapper") private val objectMapper: ObjectMapper
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
        runCatching {
            executeWithRetry(maxRetries = 1, onRetry = { attempt, delay, e ->
                log.warn(
                    "Retrying record processing (attempt {}, offset={}, delay {}ms): {}",
                    attempt, record.offset(), delay, e.message
                )
            }) {
                val envelope = objectMapper.readValue(record.value(), DebeziumEnvelope::class.java)
                    .takeIf { it.op in CREATE_OPS }

                val article = envelope
                    ?.after
                    ?.toArticle()
                    ?.also { log.info("Received article CDC event: articleId={}", it.articleId) }
                    ?: run {
                        if (envelope != null) {
                            log.warn("Received create event with null 'after' payload, offset={}", record.offset())
                        } else {
                            log.debug("Ignoring non-create CDC event, offset={}", record.offset())
                        }
                        null
                    }

                article?.let { articleAnalysisService.analyze(it) }
            }
        }.onFailure { e ->
            when (e) {
                is ArticleAnalysisException ->
                    log.error("Failed to analyze article {} at offset={}: {}", e.articleId, record.offset(), e.message, e)
                else ->
                    log.error("Failed to process article event at offset={}: {}", record.offset(), e.message, e)
            }
        }
    }
}