package com.vonkernel.lit.analyzer.adapter.outbound.dlq

import com.vonkernel.lit.analyzer.domain.port.DlqPublisher
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDlqPublisher(
    @param:Qualifier("dlqKafkaTemplate") private val kafkaTemplate: KafkaTemplate<String, String>,
    @param:Value("\${kafka.topic.article-events-dlq}") private val dlqTopic: String
) : DlqPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(originalMessage: String, retryCount: Int, articleId: String?) {
        val record = ProducerRecord<String, String>(dlqTopic, articleId, originalMessage).apply {
            headers().add("dlq-retry-count", retryCount.toString().toByteArray())
        }

        try {
            kafkaTemplate.send(record).get()
            log.info("Published to DLQ: articleId={}, retryCount={}", articleId, retryCount)
        } catch (e: Exception) {
            log.error("Failed to publish to DLQ: articleId={}, retryCount={}", articleId, retryCount, e)
            throw e
        }
    }
}
