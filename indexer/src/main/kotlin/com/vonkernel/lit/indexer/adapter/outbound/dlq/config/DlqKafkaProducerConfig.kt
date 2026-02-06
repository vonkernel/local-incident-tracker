package com.vonkernel.lit.indexer.adapter.outbound.dlq.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class DlqKafkaProducerConfig(
    @param:Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String
) {

    @Bean("dlqKafkaTemplate")
    fun dlqKafkaTemplate(): KafkaTemplate<String, String> = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        ).let { KafkaTemplate(DefaultKafkaProducerFactory(it)) }
}
