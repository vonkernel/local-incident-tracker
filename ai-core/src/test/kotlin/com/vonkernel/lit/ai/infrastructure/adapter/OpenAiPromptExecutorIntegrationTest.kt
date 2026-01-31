package com.vonkernel.lit.ai.infrastructure.adapter

import com.vonkernel.lit.ai.domain.model.LlmModel
import com.vonkernel.lit.ai.domain.model.Prompt
import com.vonkernel.lit.ai.domain.model.PromptParameters
import com.vonkernel.lit.ai.domain.model.SummarizeInput
import com.vonkernel.lit.ai.domain.model.SummarizeOutput
import com.vonkernel.lit.ai.adapter.executor.openai.OpenAiPromptExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * OpenAiPromptExecutor 통합 테스트 (실제 OpenAI API 호출)
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
 * 1. 실제 OpenAI API 호출 및 응답 파싱
 * 2. JSON 구조 검증 (내용은 예측 불가능하므로 구조만 검증)
 * 3. 토큰 사용량 메타데이터 검증
 */
@SpringBootTest
@Tag("integration")
class OpenAiPromptExecutorIntegrationTest {

    @Autowired
    private lateinit var executor: OpenAiPromptExecutor

    @Test
    fun `실제 OpenAI API로 summarize 실행`() = runBlocking {
        // Given
        val prompt = Prompt(
            id = "summarize",
            model = LlmModel.GPT_5_MINI,
            template = """
                다음 텍스트를 50자 이내로 요약하세요.

                텍스트:
                서울 강남구에서 대규모 화재가 발생했습니다. 소방당국은 현재 진압 작업을 진행 중이며, 인명 피해는 없는 것으로 알려졌습니다.

                응답 형식: JSON
                {
                  "summary": "요약된 텍스트",
                  "keyPoints": ["핵심 포인트 1", "핵심 포인트 2"]
                }
            """.trimIndent(),
            parameters = PromptParameters(temperature = 1.0f, maxCompletionTokens = 2048),
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        val input = SummarizeInput(
            text = "서울 강남구에서 대규모 화재가 발생했습니다. 소방당국은 현재 진압 작업을 진행 중이며, 인명 피해는 없는 것으로 알려졌습니다.",
            maxLength = 50
        )

        // When
        val result = executor.execute(prompt, input)

        // Then - 구조 검증 (내용은 LLM이 생성하므로 예측 불가능)
        assertNotNull(result)
        assertNotNull(result.result)
        assertNotNull(result.result.summary)
        assertNotNull(result.result.keyPoints)
        assertTrue(result.result.summary.isNotBlank())
        assertTrue(result.result.keyPoints.isNotEmpty())

        // 메타데이터 검증
        assertNotNull(result.metadata)
        assertTrue(result.metadata.tokenUsage.totalTokens > 0)
        assertTrue(result.metadata.tokenUsage.promptTokens > 0)
        assertTrue(result.metadata.tokenUsage.completionTokens > 0)

        // 결과 출력 (수동 확인용)
        println("=== OpenAI API Integration Test Result ===")
        println("Summary: ${result.result.summary}")
        println("KeyPoints: ${result.result.keyPoints}")
        println("Token Usage: ${result.metadata.tokenUsage}")
        println("Model: ${result.metadata.model}")
        println("Finish Reason: ${result.metadata.finishReason}")
    }
}