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

    @KafkaListener(
        topics = ["\${kafka.topic.article-events}"],
        groupId = "\${kafka.consumer.group-id}"
    )
    fun onArticleEvent(record: ConsumerRecord<String, String>) {
        try {
            val envelope = objectMapper.readValue(record.value(), DebeziumEnvelope::class.java)

            if (envelope.op != "c" && envelope.op != "r") {
                log.debug("Ignoring non-create CDC event: op={}", envelope.op)
                return
            }

            val article = envelope.after?.toArticle()
            if (article == null) {
                log.warn("Received create event with null 'after' payload, offset={}", record.offset())
                return
            }

            log.info("Received article CDC event: articleId={}", article.articleId)

            runBlocking {
                articleAnalysisService.analyze(article)
            }
        } catch (e: Exception) {
            log.error("Failed to process article event at offset={}: {}", record.offset(), e.message, e)
            throw e
        }
    }
}
