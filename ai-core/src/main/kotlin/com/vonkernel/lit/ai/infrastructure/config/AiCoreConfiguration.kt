package com.vonkernel.lit.ai.infrastructure.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ai-core 모듈 설정
 *
 * - ObjectMapper: JSON 직렬화/역직렬화
 * - Spring AI ChatModel은 spring-ai-openai-spring-boot-starter가 자동 설정
 */
@Configuration
class AiCoreConfiguration {

    /**
     * JSON 직렬화/역직렬화용 ObjectMapper
     *
     * - Kotlin 지원
     * - Java Time API 지원
     * - 알 수 없는 필드 무시
     */
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            // Kotlin 모듈 등록
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, false)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, false)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build()
            )

            // Java Time API 지원
            registerModule(JavaTimeModule())

            // 설정
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }
}
