package com.vonkernel.lit.ai.infrastructure.adapter

import com.vonkernel.lit.ai.adapter.executor.openai.OpenAiEmbeddingExecutor
import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * OpenAiEmbeddingExecutor 통합 테스트 (실제 OpenAI API 호출)
 *
 * 실행 조건:
 * - 환경 변수 SPRING_AI_OPENAI_API_KEY 설정 필요
 * - @Tag("integration")으로 태그 지정
 *
 * 실행 방법:
 * ```bash
 * export SPRING_AI_OPENAI_API_KEY=sk-...
 * ./gradlew ai-core:test --tests "*IntegrationTest"
 * # 또는
 * ./gradlew ai-core:test -Dgroups=integration
 * ```
 *
 * 검증 항목:
 * 1. 실제 OpenAI Embedding API 호출 및 FloatArray 반환
 * 2. 지정한 차원 수와 반환 벡터 크기 일치
 * 3. 벡터 값 범위 검증
 */
@SpringBootTest
@Tag("integration")
class OpenAiEmbeddingExecutorIntegrationTest {

    @Autowired
    private lateinit var executor: OpenAiEmbeddingExecutor

    @Test
    fun `실제 OpenAI API로 텍스트 임베딩 생성`() = runBlocking {
        // Given
        val text = "서울 강남구에서 대규모 화재가 발생했습니다."
        val dimensions = 128

        // When
        val result = executor.embed(
            text = text,
            model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
            dimensions = dimensions
        )

        // Then - 구조 검증
        assertNotNull(result)
        assertEquals(dimensions, result.size)

        // 벡터 값이 유한한 실수인지 검증
        assertTrue(result.all { it.isFinite() })

        // 영벡터가 아닌지 검증
        assertTrue(result.any { it != 0.0f })

        // 결과 출력 (수동 확인용)
        println("=== OpenAI Embedding API Integration Test Result ===")
        println("Input text: $text")
        println("Dimensions: ${result.size}")
        println("First 10 values: ${result.take(10).toList()}")
        println("Min: ${result.min()}, Max: ${result.max()}")
    }
}
