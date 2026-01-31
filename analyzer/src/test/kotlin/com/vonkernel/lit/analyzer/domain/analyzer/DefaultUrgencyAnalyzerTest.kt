package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.ai.domain.model.ExecutionMetadata
import com.vonkernel.lit.ai.domain.model.PromptExecutionResult
import com.vonkernel.lit.ai.domain.model.TokenUsage
import com.vonkernel.lit.analyzer.adapter.outbound.analyzer.DefaultUrgencyAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyAssessmentInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyAssessmentOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyItem
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.repository.UrgencyRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultUrgencyAnalyzer 테스트")
class DefaultUrgencyAnalyzerTest {

    private val promptOrchestrator: PromptOrchestrator = mockk()
    private val urgencyRepository: UrgencyRepository = mockk()
    private val analyzer = DefaultUrgencyAnalyzer(promptOrchestrator, urgencyRepository)

    private val dummyMetadata = ExecutionMetadata(
        model = "test-model",
        tokenUsage = TokenUsage(100, 50, 150),
        finishReason = "stop"
    )

    @Test
    @DisplayName("LLM 결과가 DB에 존재하는 긴급도와 매칭되면 해당 Urgency를 반환한다")
    fun analyze_matchingName_returnsDbUrgency() = runTest {
        // Given
        val dbUrgencies = listOf(
            Urgency(name = "긴급", level = 3),
            Urgency(name = "중요", level = 2),
            Urgency(name = "정보", level = 1)
        )
        every { urgencyRepository.findAll() } returns dbUrgencies

        val llmOutput = UrgencyAssessmentOutput(
            urgency = UrgencyItem(name = "긴급", level = 3)
        )
        coEvery {
            promptOrchestrator.execute(
                promptId = "urgency-assessment",
                input = any<UrgencyAssessmentInput>(),
                inputType = UrgencyAssessmentInput::class.java,
                outputType = UrgencyAssessmentOutput::class.java
            )
        } returns PromptExecutionResult(llmOutput, dummyMetadata)

        // When
        val result = analyzer.analyze("긴급 속보", "대형 화재가 발생했습니다.")

        // Then
        assertThat(result.name).isEqualTo("긴급")
        assertThat(result.level).isEqualTo(3)
    }

    @Test
    @DisplayName("LLM 결과가 DB에 없는 긴급도명이면 fallback으로 새 Urgency를 생성한다")
    fun analyze_unknownName_createsFallbackUrgency() = runTest {
        // Given
        val dbUrgencies = listOf(
            Urgency(name = "긴급", level = 3),
            Urgency(name = "중요", level = 2)
        )
        every { urgencyRepository.findAll() } returns dbUrgencies

        val llmOutput = UrgencyAssessmentOutput(
            urgency = UrgencyItem(name = "알수없는등급", level = 5)
        )
        coEvery {
            promptOrchestrator.execute(
                promptId = "urgency-assessment",
                input = any<UrgencyAssessmentInput>(),
                inputType = UrgencyAssessmentInput::class.java,
                outputType = UrgencyAssessmentOutput::class.java
            )
        } returns PromptExecutionResult(llmOutput, dummyMetadata)

        // When
        val result = analyzer.analyze("기사 제목", "기사 내용")

        // Then
        assertThat(result.name).isEqualTo("알수없는등급")
        assertThat(result.level).isEqualTo(5)
    }
}
