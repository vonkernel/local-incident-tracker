package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.ai.domain.model.ExecutionMetadata
import com.vonkernel.lit.ai.domain.model.PromptExecutionResult
import com.vonkernel.lit.ai.domain.model.TokenUsage
import com.vonkernel.lit.analyzer.adapter.outbound.analyzer.DefaultLocationValidator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationExtractionOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationValidationInput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DefaultLocationValidator 테스트")
class DefaultLocationValidatorTest {

    private val promptOrchestrator: PromptOrchestrator = mockk()
    private val validator = DefaultLocationValidator(promptOrchestrator)

    private val dummyMetadata = ExecutionMetadata(
        model = "test-model",
        tokenUsage = TokenUsage(100, 50, 150),
        finishReason = "stop"
    )

    @Test
    @DisplayName("빈 리스트 입력 시 프롬프트 호출 없이 빈 리스트를 반환한다")
    fun validate_emptyCandidates_returnsEmptyWithoutPromptCall() = runTest {
        // When
        val result = validator.validate("제목", "내용", emptyList())

        // Then
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { promptOrchestrator.execute<LocationValidationInput, LocationExtractionOutput>(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("LLM이 필터링 + 정규화 + 정제된 결과를 반환한다")
    fun validate_returnsFilteredAndNormalizedLocations() = runTest {
        // Given
        val candidates = listOf(
            ExtractedLocation("충남 부여군 세도면", LocationType.ADDRESS),
            ExtractedLocation("부여", LocationType.ADDRESS),
            ExtractedLocation("서울역 인근", LocationType.LANDMARK)
        )
        val validatedOutput = LocationExtractionOutput(
            locations = listOf(
                ExtractedLocation("충청남도 부여군 세도면", LocationType.ADDRESS),
                ExtractedLocation("서울역", LocationType.LANDMARK)
            )
        )

        coEvery {
            promptOrchestrator.execute(
                promptId = "location-validation",
                input = any<LocationValidationInput>(),
                inputType = LocationValidationInput::class.java,
                outputType = LocationExtractionOutput::class.java
            )
        } returns PromptExecutionResult(validatedOutput, dummyMetadata)

        // When
        val result = validator.validate("충남 부여군 화재", "충남 부여군 세도면에서 화재가 발생했습니다.", candidates)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("충청남도 부여군 세도면")
        assertThat(result[0].type).isEqualTo(LocationType.ADDRESS)
        assertThat(result[1].name).isEqualTo("서울역")
        assertThat(result[1].type).isEqualTo(LocationType.LANDMARK)
    }
}
