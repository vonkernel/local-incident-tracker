package com.vonkernel.lit.ai.adapter.executor.openai

import com.vonkernel.lit.ai.domain.exception.*
import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider
import com.vonkernel.lit.ai.domain.port.EmbeddingExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.stereotype.Component
import org.springframework.ai.embedding.EmbeddingModel as SpringAiEmbeddingModel

/**
 * OpenAI API를 호출하는 EmbeddingExecutor 구현체
 *
 * Hexagonal Architecture의 Adapter 역할:
 * - Domain Port(EmbeddingExecutor)의 구현체
 * - Spring AI의 EmbeddingModel을 사용하여 OpenAI Embedding API 호출
 * - Spring AI 응답을 FloatArray로 변환
 *
 * @property embeddingModel Spring AI의 EmbeddingModel (OpenAI 구현)
 */
@Component
class OpenAiEmbeddingExecutor(
    private val embeddingModel: SpringAiEmbeddingModel
) : EmbeddingExecutor {

    companion object {
        private const val SINGLE_TIMEOUT_MS = 30_000L
        private const val BATCH_TIMEOUT_MS = 60_000L
    }

    override fun supports(provider: LlmProvider): Boolean =
        provider == LlmProvider.OPENAI

    override suspend fun embed(
        text: String,
        model: EmbeddingModel,
        dimensions: Int
    ): FloatArray =
        buildEmbeddingRequest(listOf(text), model, dimensions)
            .let { callOrThrow(it, SINGLE_TIMEOUT_MS) }
            .let { extractSingleOutput(it) }

    override suspend fun embedAll(
        texts: List<String>,
        model: EmbeddingModel,
        dimensions: Int
    ): List<FloatArray> =
        buildEmbeddingRequest(texts, model, dimensions)
            .let { callOrThrow(it, BATCH_TIMEOUT_MS) }
            .let { validateAndExtractOutputs(it, texts.size) }

    // ========== Pure Functions ==========

    private fun buildEmbeddingRequest(texts: List<String>, model: EmbeddingModel, dimensions: Int): EmbeddingRequest =
        EmbeddingRequest(
            texts,
            OpenAiEmbeddingOptions.builder()
                .model(model.modelId)
                .dimensions(dimensions)
                .build()
        )

    private fun extractSingleOutput(response: EmbeddingResponse): FloatArray =
        response.results.firstOrNull()?.output
            ?: throw LlmApiException(
                statusCode = null,
                errorBody = null,
                message = "No embedding result in response"
            )

    private fun validateAndExtractOutputs(response: EmbeddingResponse, expectedSize: Int): List<FloatArray> {
        if (response.results.size != expectedSize) {
            throw LlmApiException(
                statusCode = null,
                errorBody = null,
                message = "Expected $expectedSize embeddings but got ${response.results.size}"
            )
        }
        return response.results.map { it.output }
    }

    // ========== Side Effect ==========

    private suspend fun callOrThrow(request: EmbeddingRequest, timeoutMs: Long): EmbeddingResponse =
        withContext(Dispatchers.IO) {
            runCatching { withTimeout(timeoutMs) { embeddingModel.call(request) } }
                .getOrElse { mapException(it, timeoutMs) }
        }

    private fun mapException(e: Throwable, timeoutMs: Long): Nothing =
        when (e) {
            is AiCoreException -> throw e
            is TimeoutCancellationException -> throw LlmTimeoutException(timeoutMs = timeoutMs)
            else -> throwMappedException(e)
        }

    private fun throwMappedException(e: Throwable): Nothing {
        val message = e.message.orEmpty()
        when {
            message.containsAny("401", "Unauthorized") -> throwAuthenticationException()
            message.containsAny("429", "rate limit") -> throwRateLimitException()
            else -> throwApiException(e)
        }
    }

    private fun throwAuthenticationException(): Nothing =
        throw LlmAuthenticationException(
            message = "OpenAI API authentication failed. Please check your API key."
        )

    private fun throwRateLimitException(): Nothing =
        throw LlmRateLimitException(
            retryAfterSeconds = null,
            message = "OpenAI API rate limit exceeded"
        )

    private fun throwApiException(e: Throwable): Nothing =
        throw LlmApiException(
            statusCode = null,
            errorBody = e.message,
            message = "OpenAI Embedding API error: ${e.message}",
            cause = e
        )

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it) }
}
