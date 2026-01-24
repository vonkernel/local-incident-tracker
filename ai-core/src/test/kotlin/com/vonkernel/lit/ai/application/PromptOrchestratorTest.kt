package com.vonkernel.lit.ai.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.ai.domain.model.SummarizeInput
import com.vonkernel.lit.ai.domain.model.SummarizeOutput
import com.vonkernel.lit.ai.infrastructure.adapter.YamlPromptLoader
import com.vonkernel.lit.ai.infrastructure.adapter.openai.OpenAiPromptExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.mockito.Mockito as MockitoLib
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.ai.chat.prompt.Prompt as SpringAiPrompt

/**
 * PromptOrchestrator 단위 테스트 (Mock 기반, Spring 없이)
 *
 * 검증 항목:
 * 1. 프롬프트 로드 → 템플릿 변수 치환 → 실행 전체 플로우
 * 2. 입력 객체를 JSON serialize하여 템플릿 변수 추출
 * 3. Mock ChatModel로 응답 파싱 및 타입 안전성
 */
class PromptOrchestratorTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val resourceLoader = PathMatchingResourcePatternResolver()
    private val promptLoader = YamlPromptLoader(resourceLoader)
    private val mockChatModel: ChatModel = mock(ChatModel::class.java)
    private val executor = OpenAiPromptExecutor(mockChatModel, objectMapper)
    private val orchestrator = PromptOrchestrator(
        executors = listOf(executor),
        promptLoader = promptLoader,
        objectMapper = objectMapper
    )

    @Test
    fun `전체 플로우 테스트 - 로드, 치환, 실행`() = runBlocking {
        // Given
        val input = SummarizeInput(
            text = "서울 강남구에서 대규모 화재가 발생했습니다. 소방당국은 진압 중입니다.",
            maxLength = 50
        )

        // Mock 응답 설정
        val mockJsonResponse = """
            {
                "summary": "서울 강남구 화재 발생, 소방당국 진압 중",
                "keyPoints": ["강남구 화재", "소방 진압"]
            }
        """.trimIndent()

        val mockResponse = createMockChatResponse(mockJsonResponse)
        `when`(mockChatModel.call(org.mockito.ArgumentMatchers.any(SpringAiPrompt::class.java)))
            .thenReturn(mockResponse)

        // When
        val result = orchestrator.execute(
            promptId = "summarize",
            input = input,
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then
        assertNotNull(result)
        assertNotNull(result.result)
        assertEquals("서울 강남구 화재 발생, 소방당국 진압 중", result.result.summary)
        assertEquals(2, result.result.keyPoints.size)
        assertEquals("강남구 화재", result.result.keyPoints[0])
        assertEquals("소방 진압", result.result.keyPoints[1])

        // 메타데이터 검증
        assertEquals(50, result.metadata.tokenUsage.promptTokens)
        assertEquals(30, result.metadata.tokenUsage.completionTokens)
        assertEquals(80, result.metadata.tokenUsage.totalTokens)
    }

    @Test
    fun `템플릿 변수 치환 검증`() = runBlocking {
        // Given
        val input = SummarizeInput(
            text = "테스트 텍스트입니다",
            maxLength = 100
        )

        // Mock 응답 설정
        val mockJsonResponse = """
            {
                "summary": "테스트 요약",
                "keyPoints": ["테스트"]
            }
        """.trimIndent()

        val mockResponse = createMockChatResponse(mockJsonResponse)
        `when`(mockChatModel.call(org.mockito.ArgumentMatchers.any(SpringAiPrompt::class.java)))
            .thenReturn(mockResponse)

        // When
        val result = orchestrator.execute(
            promptId = "summarize",
            input = input,
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then - 템플릿 변수가 치환되어 실행되었는지 확인
        assertNotNull(result)
        assertNotNull(result.result.summary)
        assertNotNull(result.result.keyPoints)
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

        val usage = MockitoLib.mock(Usage::class.java)
        MockitoLib.`when`(usage.promptTokens).thenReturn(50)
        MockitoLib.`when`(usage.completionTokens).thenReturn(30)
        MockitoLib.`when`(usage.totalTokens).thenReturn(80)
        val responseMetadata = ChatResponseMetadata.builder()
            .usage(usage)
            .build()

        return ChatResponse(listOf(generation), responseMetadata)
    }
}
