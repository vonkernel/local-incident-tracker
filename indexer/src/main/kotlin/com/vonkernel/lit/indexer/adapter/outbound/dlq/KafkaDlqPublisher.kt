package com.vonkernel.lit.indexer.adapter.outbound.dlq

import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDlqPublisher(
    @param:Qualifier("dlqKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>,
    @param:Value("\${kafka.topic.analysis-events-dlq}") private val dlqTopic: String
) : DlqPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(originalMessage: String, retryCount: Int, articleId: String?) {
        ProducerRecord<String, String>(dlqTopic, articleId, originalMessage)
            .apply { headers().add("dlq-retry-count", retryCount.toString().toByteArray()) }
            .let { record ->
                runCatching { withContext(Dispatchers.IO) { kafkaTemplate.send(record).get() } }
                    .onSuccess { log.info("Published to DLQ: articleId={}, retryCount={}", articleId, retryCount) }
                    .onFailure { e -> log.error("Failed to publish to DLQ: articleId={}, retryCount={}", articleId, retryCount, e) }
                    .getOrThrow()
            }
    }
}
