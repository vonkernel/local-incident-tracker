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

    override fun supports(provider: LlmProvider): Boolean =
        provider == LlmProvider.OPENAI

    override suspend fun embed(
        text: String,
        model: EmbeddingModel,
        dimensions: Int
    ): FloatArray = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(30_000L) {
                embeddingModel.call(
                    EmbeddingRequest(
                        listOf(text),
                        OpenAiEmbeddingOptions.builder()
                            .model(model.modelId)
                            .dimensions(dimensions)
                            .build()
                    )
                )
            }
        }.fold(
            onSuccess = { response ->
                response.results.firstOrNull()?.output
                    ?: throw LlmApiException(
                        statusCode = null,
                        errorBody = null,
                        message = "No embedding result in response"
                    )
            },
            onFailure = { e ->
                when (e) {
                    is AiCoreException -> throw e
                    is TimeoutCancellationException -> throw LlmTimeoutException(timeoutMs = 30_000L)
                    else -> handleException(e)
                }
            }
        )
    }

    /**
     * 예외 처리 및 Domain Exception으로 변환
     */
    private fun handleException(e: Throwable): Nothing =
        when {
            e.message?.let { it.contains("401") || it.contains("Unauthorized") } == true ->
                throw LlmAuthenticationException(
                    message = "OpenAI API authentication failed. Please check your API key."
                )

            e.message?.let { it.contains("429") || it.contains("rate limit") } == true ->
                throw LlmRateLimitException(
                    retryAfterSeconds = null,
                    message = "OpenAI API rate limit exceeded"
                )

            else ->
                throw LlmApiException(
                    statusCode = null,
                    errorBody = e.message,
                    message = "OpenAI Embedding API error: ${e.message}",
                    cause = e
                )
        }
}
