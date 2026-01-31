package com.vonkernel.lit.ai.infrastructure.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.ai.domain.exception.ResponseParsingException
import com.vonkernel.lit.ai.domain.model.LlmModel
import com.vonkernel.lit.ai.domain.model.Prompt
import com.vonkernel.lit.ai.domain.model.PromptParameters
import com.vonkernel.lit.ai.domain.model.SummarizeInput
import com.vonkernel.lit.ai.domain.model.SummarizeOutput
import com.vonkernel.lit.ai.adapter.executor.openai.OpenAiPromptExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.mockito.Mockito
import org.springframework.ai.chat.prompt.Prompt as SpringAiPrompt

/**
 * OpenAiPromptExecutor 단위 테스트 (Mock 기반)
 *
 * 검증 항목:
 * 1. Mock 응답 파싱 성공
 * 2. JSON 파싱 실패 → ResponseParsingException
 * 3. 토큰 사용량 메타데이터 정상 변환
 */
class OpenAiPromptExecutorTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val mockChatModel: ChatModel = mock(ChatModel::class.java)
    private val executor = OpenAiPromptExecutor(mockChatModel, objectMapper)

    @Test
    fun `Mock ChatModel로 정상 응답 파싱`() = runBlocking {
        // Given
        val prompt = Prompt(
            id = "summarize",
            model = LlmModel.GPT_5_MINI,
            template = "다음 텍스트를 100자 이내로 요약하세요.\n\n텍스트:\n서울에서 화재 발생",
            parameters = PromptParameters(temperature = 1.0f),
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        val input = SummarizeInput(
            text = "서울에서 화재 발생",
            maxLength = 100
        )

        // Mock 응답 JSON
        val mockJsonResponse = """
            {
                "summary": "서울 화재 발생",
                "keyPoints": ["서울", "화재"]
            }
        """.trimIndent()

        val mockResponse = createMockChatResponse(mockJsonResponse)
        `when`(mockChatModel.call(org.mockito.ArgumentMatchers.any(SpringAiPrompt::class.java)))
            .thenReturn(mockResponse)

        // When
        val result = executor.execute(prompt, input)

        // Then
        assertNotNull(result)
        assertEquals("서울 화재 발생", result.result.summary)
        assertEquals(2, result.result.keyPoints.size)
        assertEquals("서울", result.result.keyPoints[0])
        assertEquals("화재", result.result.keyPoints[1])

        // 메타데이터 검증
        assertEquals(50, result.metadata.tokenUsage.promptTokens)
        assertEquals(30, result.metadata.tokenUsage.completionTokens)
        assertEquals(80, result.metadata.tokenUsage.totalTokens)
    }

    @Test
    fun `JSON 파싱 실패 시 ResponseParsingException 발생`() = runBlocking {
        // Given
        val prompt = Prompt(
            id = "summarize",
            model = LlmModel.GPT_5_MINI,
            template = "Test template",
            parameters = PromptParameters(),
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        val input = SummarizeInput(text = "test", maxLength = 100)

        // Invalid JSON 응답
        val invalidJsonResponse = "This is not a valid JSON"
        val mockResponse = createMockChatResponse(invalidJsonResponse)
        `when`(mockChatModel.call(org.mockito.ArgumentMatchers.any(SpringAiPrompt::class.java)))
            .thenReturn(mockResponse)

        // When & Then
        val exception = assertThrows<ResponseParsingException> {
            executor.execute(prompt, input)
        }

        assert(exception.message!!.contains("JSON parsing failed") ||
               exception.message!!.contains("Unrecognized token"))
    }

    /**
     * Mock ChatResponse 생성 헬퍼 함수
     */
    private fun createMockChatResponse(content: String): ChatResponse {
        val assistantMessage = AssistantMessage(content)
        val metadata = ChatGenerationMetadata.builder()
            .finishReason("stop")
            .build()
        val generation = Generation(assistantMessage, metadata)

        val usage = Mockito.mock(Usage::class.java)
        Mockito.`when`(usage.promptTokens).thenReturn(50)
        Mockito.`when`(usage.completionTokens).thenReturn(30)
        Mockito.`when`(usage.totalTokens).thenReturn(80)
        val responseMetadata = ChatResponseMetadata.builder()
            .usage(usage)
            .build()

        return ChatResponse(listOf(generation), responseMetadata)
    }
}
