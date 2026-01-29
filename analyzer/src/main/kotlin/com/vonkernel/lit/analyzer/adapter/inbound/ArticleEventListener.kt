package com.vonkernel.lit.analyzer.adapter.inbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.adapter.inbound.model.toArticle
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import kotlinx.coroutines.runBlocking
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
    fun onArticleEvent(record: ConsumerRecord<String, String>) {
        runCatching { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) }
            .map { envelope -> envelope.takeIf { it.op in CREATE_OPS } }
            .mapCatching { envelope ->
                envelope
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
            }
            .mapCatching { article ->
                article?.let { runBlocking { articleAnalysisService.analyze(it) } }
            }
            .onFailure { e ->
                log.error("Failed to process article event at offset={}: {}", record.offset(), e.message, e)
                throw e
            }
    }
}
