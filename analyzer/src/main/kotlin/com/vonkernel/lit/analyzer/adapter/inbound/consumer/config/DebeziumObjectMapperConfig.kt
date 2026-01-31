package com.vonkernel.lit.analyzer.adapter.inbound.consumer.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DebeziumObjectMapperConfig {

    @Bean("debeziumObjectMapper")
    fun debeziumObjectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
