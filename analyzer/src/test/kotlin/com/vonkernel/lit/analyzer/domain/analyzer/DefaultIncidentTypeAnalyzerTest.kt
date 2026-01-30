package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.ai.domain.model.ExecutionMetadata
import com.vonkernel.lit.ai.domain.model.PromptExecutionResult
import com.vonkernel.lit.ai.domain.model.TokenUsage
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationInput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationOutput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeItem
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.repository.IncidentTypeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultIncidentTypeAnalyzer 테스트")
class DefaultIncidentTypeAnalyzerTest {

    private val promptOrchestrator: PromptOrchestrator = mockk()
    private val incidentTypeRepository: IncidentTypeRepository = mockk()
    private val analyzer = DefaultIncidentTypeAnalyzer(promptOrchestrator, incidentTypeRepository)

    private val dummyMetadata = ExecutionMetadata(
        model = "test-model",
        tokenUsage = TokenUsage(100, 50, 150),
        finishReason = "stop"
    )

    @Test
    @DisplayName("LLM 결과가 DB에 존재하는 코드와 매칭되면 해당 IncidentType을 반환한다")
    fun analyze_matchingCodes_returnsMatchedIncidentTypes() = runTest {
        // Given
        val dbTypes = listOf(
            IncidentType(code = "forest_fire", name = "산불"),
            IncidentType(code = "flood", name = "홍수"),
            IncidentType(code = "typhoon", name = "태풍")
        )
        every { incidentTypeRepository.findAll() } returns dbTypes

        val llmOutput = IncidentTypeClassificationOutput(
            incidentTypes = listOf(
                IncidentTypeItem(code = "forest_fire", name = "산불"),
                IncidentTypeItem(code = "flood", name = "홍수")
            )
        )
        coEvery {
            promptOrchestrator.execute(
                promptId = "incident-type-classification",
                input = any<IncidentTypeClassificationInput>(),
                inputType = IncidentTypeClassificationInput::class.java,
                outputType = IncidentTypeClassificationOutput::class.java
            )
        } returns PromptExecutionResult(llmOutput, dummyMetadata)

        // When
        val result = analyzer.analyze("산불 발생", "강원도에서 산불이 발생했습니다.")

        // Then
        assertThat(result).hasSize(2)
        assertThat(result.map { it.code }).containsExactlyInAnyOrder("forest_fire", "flood")
    }

    @Test
    @DisplayName("LLM이 DB에 없는 코드를 반환하면 mapNotNull로 필터링된다")
    fun analyze_unknownCode_filteredOut() = runTest {
        // Given
        val dbTypes = listOf(
            IncidentType(code = "forest_fire", name = "산불")
        )
        every { incidentTypeRepository.findAll() } returns dbTypes

        val llmOutput = IncidentTypeClassificationOutput(
            incidentTypes = listOf(
                IncidentTypeItem(code = "forest_fire", name = "산불"),
                IncidentTypeItem(code = "unknown_type", name = "알수없음")
            )
        )
        coEvery {
            promptOrchestrator.execute(
                promptId = "incident-type-classification",
                input = any<IncidentTypeClassificationInput>(),
                inputType = IncidentTypeClassificationInput::class.java,
                outputType = IncidentTypeClassificationOutput::class.java
            )
        } returns PromptExecutionResult(llmOutput, dummyMetadata)

        // When
        val result = analyzer.analyze("재난 발생", "재난이 발생했습니다.")

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.first().code).isEqualTo("forest_fire")
    }

    @Test
    @DisplayName("LLM이 빈 결과를 반환하면 빈 Set을 반환한다")
    fun analyze_emptyLlmResult_returnsEmptySet() = runTest {
        // Given
        val dbTypes = listOf(
            IncidentType(code = "forest_fire", name = "산불")
        )
        every { incidentTypeRepository.findAll() } returns dbTypes

        val llmOutput = IncidentTypeClassificationOutput(incidentTypes = emptyList())
        coEvery {
            promptOrchestrator.execute(
                promptId = "incident-type-classification",
                input = any<IncidentTypeClassificationInput>(),
                inputType = IncidentTypeClassificationInput::class.java,
                outputType = IncidentTypeClassificationOutput::class.java
            )
        } returns PromptExecutionResult(llmOutput, dummyMetadata)

        // When
        val result = analyzer.analyze("기사 제목", "기사 내용")

        // Then
        assertThat(result).isEmpty()
    }
}
