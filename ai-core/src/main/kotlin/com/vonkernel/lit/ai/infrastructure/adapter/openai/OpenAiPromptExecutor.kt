package com.vonkernel.lit.ai.infrastructure.adapter.openai

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
    private val objectMapper: ObjectMapper
) : PromptExecutor {

    override fun supports(provider: LlmProvider): Boolean {
        return provider == LlmProvider.OPENAI
    }

    override suspend fun <I, O> execute(
        prompt: Prompt<I, O>,
        input: I
    ): PromptExecutionResult<O> = withContext(Dispatchers.IO) {
        // ChatOptions 구성
        val chatOptions = buildChatOptions(prompt.parameters)

        try {
            // LLM 호출 (30초 타임아웃)
            val response = withTimeout(30000L) {
                chatModel.call(
                    SpringAiPrompt(
                        prompt.template,
                        chatOptions
                    )
                )
            }

            // 응답 파싱
            parseResponse(prompt, response)

        } catch (e: AiCoreException) {
            // Domain Exception은 그대로 throw
            throw e
        } catch (e: TimeoutCancellationException) {
            throw LlmTimeoutException(
                timeoutMs = 30000L
            )
        } catch (e: Exception) {
            handleException(e)
        }
    }

    /**
     * PromptParameters를 Spring AI의 OpenAiChatOptions로 변환
     */
    private fun buildChatOptions(params: PromptParameters): OpenAiChatOptions {
        // OpenAI 전용 옵션 변환
        val openAiOptions = OpenAiSpecificOptions.fromMap(params.providerSpecificOptions)

        return OpenAiChatOptions.builder()
            .apply {
                // 공통 파라미터 (Spring AI 2.0.0-M1에서 with prefix 제거됨)
                temperature(params.temperature.toDouble())
                params.maxTokens?.let { maxTokens(it) }
                params.maxCompletionTokens?.let { maxCompletionTokens(it) }
                params.topP?.let { topP(it.toDouble()) }
                params.frequencyPenalty?.let { frequencyPenalty(it.toDouble()) }
                params.presencePenalty?.let { presencePenalty(it.toDouble()) }
                params.stopSequences?.let { stop(it) }

                // OpenAI 전용 파라미터
                openAiOptions?.let { options ->
                    // responseFormat 설정
                    options.responseFormat?.let { responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build()) }
                    options.seed?.let { seed(it) }
                    options.user?.let { user(it) }
                }
            }
            .build()
    }

    /**
     * Spring AI ChatResponse를 Domain PromptExecutionResult로 변환
     */
    private fun <I, O> parseResponse(
        prompt: Prompt<I, O>,
        response: ChatResponse
    ): PromptExecutionResult<O> {
        // ChatResponse.results[0].output에서 AssistantMessage 가져오기
        val generation = response.results.firstOrNull()
            ?: throw ResponseParsingException(
                promptId = prompt.id,
                responseContent = "",
                targetType = prompt.outputType.simpleName,
                message = "No generation result in ChatResponse"
            )

        // AssistantMessage.getText()로 텍스트 가져오기
        val content = generation.output.text
            ?: throw ResponseParsingException(
                promptId = prompt.id,
                responseContent = "",
                targetType = prompt.outputType.simpleName,
                message = "AssistantMessage text content is null"
            )

        // JSON 역직렬화
        val parsedResult = try {
            objectMapper.readValue(content, prompt.outputType)
        } catch (e: Exception) {
            throw ResponseParsingException(
                promptId = prompt.id,
                responseContent = content,
                targetType = prompt.outputType.simpleName,
                message = e.message ?: "JSON parsing failed",
                cause = e
            )
        }

        // 메타데이터 구성
        val usage = response.metadata?.usage
        val metadata = ExecutionMetadata(
            model = prompt.model.modelId,
            tokenUsage = TokenUsage(
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                totalTokens = usage?.totalTokens ?: 0
            ),
            finishReason = generation.metadata?.finishReason
        )

        return PromptExecutionResult(
            result = parsedResult,
            metadata = metadata
        )
    }

    /**
     * 예외 처리 및 Domain Exception으로 변환
     */
    private fun handleException(e: Exception): Nothing {
        when {
            // 인증 실패
            e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> {
                throw LlmAuthenticationException(
                    message = "OpenAI API authentication failed. Please check your API key."
                )
            }
            // Rate limit
            e.message?.contains("429") == true || e.message?.contains("rate limit") == true -> {
                throw LlmRateLimitException(
                    retryAfterSeconds = null,  // Spring AI에서 Retry-After 헤더 파싱 필요
                    message = "OpenAI API rate limit exceeded"
                )
            }
            // 기타 API 에러
            else -> {
                throw LlmApiException(
                    statusCode = null,
                    errorBody = e.message,
                    message = "OpenAI API error: ${e.message}",
                    cause = e
                )
            }
        }
    }
}
