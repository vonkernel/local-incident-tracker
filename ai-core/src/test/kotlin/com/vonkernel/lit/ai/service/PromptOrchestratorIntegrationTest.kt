package com.vonkernel.lit.ai.service

import com.vonkernel.lit.ai.domain.model.SummarizeInput
import com.vonkernel.lit.ai.domain.model.SummarizeOutput
import com.vonkernel.lit.ai.domain.service.PromptOrchestrator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * PromptOrchestrator 통합 테스트 (실제 OpenAI API 호출)
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
 * 1. YamlPromptLoader로 프롬프트 로드
 * 2. 템플릿 변수 치환 ({{text}}, {{maxLength}})
 * 3. 실제 OpenAI API 호출
 * 4. 응답 구조 검증 (내용은 예측 불가능하므로 구조만 검증)
 */
@SpringBootTest
@Tag("integration")
class PromptOrchestratorIntegrationTest {

    @Autowired
    private lateinit var orchestrator: PromptOrchestrator

    @Test
    fun `실제 OpenAI API로 summarize 전체 플로우 테스트`() = runBlocking {
        // Given
        val input = SummarizeInput(
            text = "서울 강남구에서 대규모 화재가 발생했습니다. 소방당국은 현재 진압 작업을 진행 중이며, 인명 피해는 없는 것으로 알려졌습니다.",
            maxLength = 100
        )

        // When
        val result = orchestrator.execute(
            promptId = "summarize",
            input = input,
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

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

        // 결과 출력 (수동 확인용)
        println("=== PromptOrchestrator Integration Test Result ===")
        println("Input text: ${input.text}")
        println("Input maxLength: ${input.maxLength}")
        println("Output summary: ${result.result.summary}")
        println("Output keyPoints: ${result.result.keyPoints}")
        println("Token Usage: ${result.metadata.tokenUsage}")
        println("Model: ${result.metadata.model}")
    }

    @Test
    fun `템플릿 변수 치환 검증 - 실제 API`() = runBlocking {
        // Given - 다양한 입력값으로 템플릿 변수 치환 확인
        val input = SummarizeInput(
            text = "테스트 텍스트입니다. 이것은 매우 긴 텍스트로 여러 가지 정보를 담고 있습니다. " +
                   "첫 번째 정보는 A이고, 두 번째 정보는 B입니다. 세 번째 정보는 C입니다.",
            maxLength = 100
        )

        // When
        val result = orchestrator.execute(
            promptId = "summarize",
            input = input,
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then
        assertNotNull(result)
        assertNotNull(result.result.summary)
        assertNotNull(result.result.keyPoints)
        assertTrue(result.result.summary.length <= input.maxLength + 100) // LLM이 정확히 지키지 않을 수 있음

        // 결과 출력
        println("=== Template Variable Test Result ===")
        println("Input: ${input.text}")
        println("MaxLength: ${input.maxLength}")
        println("Summary: ${result.result.summary}")
        println("KeyPoints: ${result.result.keyPoints}")
    }
}
