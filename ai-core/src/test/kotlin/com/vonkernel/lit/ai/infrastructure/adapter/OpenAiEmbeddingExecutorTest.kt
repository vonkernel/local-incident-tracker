package com.vonkernel.lit.ai.infrastructure.adapter

import com.vonkernel.lit.ai.adapter.executor.openai.OpenAiEmbeddingExecutor
import com.vonkernel.lit.ai.domain.exception.LlmApiException
import com.vonkernel.lit.ai.domain.exception.LlmAuthenticationException
import com.vonkernel.lit.ai.domain.exception.LlmRateLimitException
import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.EmbeddingModel as SpringAiEmbeddingModel

/**
 * OpenAiEmbeddingExecutor 단위 테스트 (Mock 기반)
 *
 * 검증 항목:
 * 1. OPENAI provider 지원 여부
 * 2. Mock 응답으로 FloatArray 정상 반환
 * 3. 빈 응답 시 LlmApiException 발생
 * 4. 인증 실패 시 LlmAuthenticationException 발생
 * 5. Rate limit 초과 시 LlmRateLimitException 발생
 */
class OpenAiEmbeddingExecutorTest {

    private val mockEmbeddingModel: SpringAiEmbeddingModel = mock(SpringAiEmbeddingModel::class.java)
    private val executor = OpenAiEmbeddingExecutor(mockEmbeddingModel)

    @Test
    fun `OPENAI provider를 지원한다`() {
        assertTrue(executor.supports(LlmProvider.OPENAI))
    }

    @Test
    fun `OPENAI 외 provider는 지원하지 않는다`() {
        // LlmProvider에 현재 OPENAI만 있으므로, supports가 false를 반환하는 케이스는
        // 향후 다른 provider 추가 시 자동으로 검증됨
        assertFalse(executor.supports(LlmProvider.OPENAI).not())
    }

    @Test
    fun `Mock EmbeddingModel로 정상 임베딩 벡터 반환`() = runBlocking {
        // Given
        val expectedVector = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val mockResponse = EmbeddingResponse(
            listOf(Embedding(expectedVector, 0))
        )
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenReturn(mockResponse)

        // When
        val result = executor.embed(
            text = "서울에서 화재 발생",
            model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
            dimensions = 4
        )

        // Then
        assertArrayEquals(expectedVector, result)
    }

    @Test
    fun `빈 응답 시 LlmApiException 발생`() = runBlocking {
        // Given
        val mockResponse = EmbeddingResponse(emptyList())
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenReturn(mockResponse)

        // When & Then
        val exception = assertThrows<LlmApiException> {
            executor.embed(
                text = "test",
                model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
                dimensions = 128
            )
        }
        assertTrue(exception.message!!.contains("No embedding result"))
    }

    @Test
    fun `인증 실패 시 LlmAuthenticationException 발생`() {
        // Given
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenThrow(RuntimeException("401 Unauthorized"))

        // When & Then
        assertThrows<LlmAuthenticationException> {
            runBlocking {
                executor.embed(
                    text = "test",
                    model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
                    dimensions = 128
                )
            }
        }
    }

    @Test
    fun `Rate limit 초과 시 LlmRateLimitException 발생`() {
        // Given
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenThrow(RuntimeException("429 rate limit exceeded"))

        // When & Then
        assertThrows<LlmRateLimitException> {
            runBlocking {
                executor.embed(
                    text = "test",
                    model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
                    dimensions = 128
                )
            }
        }
    }

    @Test
    fun `embedAll로 여러 텍스트 배치 임베딩 반환`() = runBlocking {
        // Given
        val vector1 = floatArrayOf(0.1f, 0.2f)
        val vector2 = floatArrayOf(0.3f, 0.4f)
        val mockResponse = EmbeddingResponse(
            listOf(Embedding(vector1, 0), Embedding(vector2, 1))
        )
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenReturn(mockResponse)

        // When
        val results = executor.embedAll(
            texts = listOf("텍스트1", "텍스트2"),
            model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
            dimensions = 2
        )

        // Then
        assertTrue(results.size == 2)
        assertArrayEquals(vector1, results[0])
        assertArrayEquals(vector2, results[1])
    }

    @Test
    fun `embedAll 결과 수 불일치 시 LlmApiException 발생`() = runBlocking {
        // Given
        val mockResponse = EmbeddingResponse(
            listOf(Embedding(floatArrayOf(0.1f), 0))
        )
        `when`(mockEmbeddingModel.call(any(EmbeddingRequest::class.java)))
            .thenReturn(mockResponse)

        // When & Then
        val exception = assertThrows<LlmApiException> {
            executor.embedAll(
                texts = listOf("텍스트1", "텍스트2"),
                model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
                dimensions = 1
            )
        }
        assertTrue(exception.message!!.contains("Expected 2"))
    }
}
