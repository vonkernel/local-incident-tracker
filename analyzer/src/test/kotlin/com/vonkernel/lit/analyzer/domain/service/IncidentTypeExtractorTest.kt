package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.port.repository.IncidentTypeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IncidentTypeExtractor 테스트")
class IncidentTypeExtractorTest {

    private val incidentTypeRepository: IncidentTypeRepository = mockk()
    private val incidentTypeAnalyzer: IncidentTypeAnalyzer = mockk()

    private lateinit var service: IncidentTypeExtractor

    private val articleId = "article-001"
    private val title = "서울 강남구 화재 발생"
    private val content = "서울 강남구에서 대형 화재가 발생했습니다."

    private val allIncidentTypes = listOf(
        IncidentType("fire", "화재"),
        IncidentType("flood", "홍수"),
        IncidentType("earthquake", "지진")
    )

    private val expectedResult = setOf(IncidentType("fire", "화재"))

    @BeforeEach
    fun setUp() {
        service = IncidentTypeExtractor(incidentTypeRepository, incidentTypeAnalyzer)
    }

    @Test
    @DisplayName("repository에서 조회한 타입으로 분석 수행")
    fun `repository에서 조회한 타입으로 분석 수행`() = runTest {
        // Given
        every { incidentTypeRepository.findAll() } returns allIncidentTypes
        coEvery { incidentTypeAnalyzer.analyze(allIncidentTypes, title, content) } returns expectedResult

        // When
        val result = service.process(articleId, title, content)

        // Then
        assertThat(result).isEqualTo(expectedResult)
        verify(exactly = 1) { incidentTypeRepository.findAll() }
        coVerify(exactly = 1) { incidentTypeAnalyzer.analyze(allIncidentTypes, title, content) }
    }

    @Test
    @DisplayName("분석 실패 시 재시도 후 성공")
    fun `분석 실패 시 재시도 후 성공`() = runTest {
        // Given
        var callCount = 0
        every { incidentTypeRepository.findAll() } answers {
            callCount++
            if (callCount < 2) throw RuntimeException("일시적 오류")
            allIncidentTypes
        }
        coEvery { incidentTypeAnalyzer.analyze(allIncidentTypes, title, content) } returns expectedResult

        // When
        val result = service.process(articleId, title, content)

        // Then
        assertThat(result).isEqualTo(expectedResult)
        verify(exactly = 2) { incidentTypeRepository.findAll() }
    }
}
