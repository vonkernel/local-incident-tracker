package com.vonkernel.lit.ai.adapter.executor.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.ai.domain.exception.*
import com.vonkernel.lit.ai.domain.model.*
import com.vonkernel.lit.ai.domain.port.PromptExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.ai.chat.prompt.Prompt as SpringAiPrompt

/**
 * OpenAI API를 호출하는 PromptExecutor 구현체
 *
 * Hexagonal Architecture의 Adapter 역할:
 * - Domain Port(PromptExecutor)의 구현체
 * - Spring AI의 ChatModel을 사용하여 OpenAI API 호출
 * - Spring AI 응답을 Domain 모델로 변환
 *
 * @property chatModel Spring AI의 ChatModel (OpenAI 구현)
 * @property objectMapper JSON 역직렬화에 사용
 */
@Component
class OpenAiPromptExecutor(
    private val chatModel: ChatModel,
    @param:Qualifier("aiCoreObjectMapper") private val objectMapper: ObjectMapper
) : PromptExecutor {

    override fun supports(provider: LlmProvider): Boolean =
        provider == LlmProvider.OPENAI

    override suspend fun <I, O> execute(
        prompt: Prompt<I, O>,
        input: I
    ): PromptExecutionResult<O> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(30000L) {
                chatModel.call(
                    SpringAiPrompt(
                        prompt.template,
                        buildChatOptions(prompt.parameters)
                    )
                )
            }
        }.fold(
            onSuccess = { response -> parseResponse(prompt, response) },
            onFailure = { e ->
                when (e) {
                    is AiCoreException -> throw e
                    is TimeoutCancellationException -> throw LlmTimeoutException(timeoutMs = 30000L)
                    else -> handleException(e)
                }
            }
        )
    }

    /**
     * PromptParameters를 Spring AI의 OpenAiChatOptions로 변환
     */
    private fun buildChatOptions(params: PromptParameters): OpenAiChatOptions =
        OpenAiChatOptions.builder()
            .apply {
                temperature(params.temperature.toDouble())
                params.maxTokens?.let { maxTokens(it) }
                params.maxCompletionTokens?.let { maxCompletionTokens(it) }
                params.topP?.let { topP(it.toDouble()) }
                params.frequencyPenalty?.let { frequencyPenalty(it.toDouble()) }
                params.presencePenalty?.let { presencePenalty(it.toDouble()) }
                params.stopSequences?.let { stop(it) }

                OpenAiSpecificOptions.fromMap(params.providerSpecificOptions)?.let { options ->
                    options.responseFormat?.let { responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build()) }
                    options.seed?.let { seed(it) }
                    options.user?.let { user(it) }
                }
            }
            .build()

    /**
     * Spring AI ChatResponse를 Domain PromptExecutionResult로 변환
     */
    private fun <I, O> parseResponse(
        prompt: Prompt<I, O>,
        response: ChatResponse
    ): PromptExecutionResult<O> =
        response.results.firstOrNull()
            ?.let { generation ->
                generation.output.text
                    ?.let { content ->
                        runCatching { objectMapper.readValue(content, prompt.outputType) }
                            .getOrElse { e ->
                                throw ResponseParsingException(
                                    promptId = prompt.id,
                                    responseContent = content,
                                    targetType = prompt.outputType.simpleName,
                                    message = e.message ?: "JSON parsing failed",
                                    cause = e
                                )
                            }
                            .let { parsedResult ->
                                PromptExecutionResult(
                                    result = parsedResult,
                                    metadata = ExecutionMetadata(
                                        model = prompt.model.modelId,
                                        tokenUsage = response.metadata?.usage.let { usage ->
                                            TokenUsage(
                                                promptTokens = usage?.promptTokens ?: 0,
                                                completionTokens = usage?.completionTokens ?: 0,
                                                totalTokens = usage?.totalTokens ?: 0
                                            )
                                        },
                                        finishReason = generation.metadata?.finishReason
                                    )
                                )
                            }
                    }
                    ?: throw ResponseParsingException(
                        promptId = prompt.id,
                        responseContent = "",
                        targetType = prompt.outputType.simpleName,
                        message = "AssistantMessage text content is null"
                    )
            }
            ?: throw ResponseParsingException(
                promptId = prompt.id,
                responseContent = "",
                targetType = prompt.outputType.simpleName,
                message = "No generation result in ChatResponse"
            )

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
                    message = "OpenAI API error: ${e.message}",
                    cause = e
                )
        }
}
