package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.port.repository.UrgencyRepository
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

@DisplayName("UrgencyExtractor 테스트")
class UrgencyExtractorTest {

    private val urgencyRepository: UrgencyRepository = mockk()
    private val urgencyAnalyzer: UrgencyAnalyzer = mockk()

    private lateinit var service: UrgencyExtractor

    private val articleId = "article-001"
    private val title = "서울 강남구 화재 발생"
    private val content = "서울 강남구에서 대형 화재가 발생했습니다."

    private val allUrgencies = listOf(
        Urgency("긴급", 3),
        Urgency("중요", 2),
        Urgency("정보", 1)
    )

    private val expectedUrgency = Urgency("긴급", 3)

    @BeforeEach
    fun setUp() {
        service = UrgencyExtractor(urgencyRepository, urgencyAnalyzer)
    }

    @Test
    @DisplayName("repository에서 조회한 긴급도로 분석 수행")
    fun `repository에서 조회한 긴급도로 분석 수행`() = runTest {
        // Given
        every { urgencyRepository.findAll() } returns allUrgencies
        coEvery { urgencyAnalyzer.analyze(allUrgencies, title, content) } returns expectedUrgency

        // When
        val result = service.process(articleId, title, content)

        // Then
        assertThat(result).isEqualTo(expectedUrgency)
        verify(exactly = 1) { urgencyRepository.findAll() }
        coVerify(exactly = 1) { urgencyAnalyzer.analyze(allUrgencies, title, content) }
    }

    @Test
    @DisplayName("분석 실패 시 재시도 후 성공")
    fun `분석 실패 시 재시도 후 성공`() = runTest {
        // Given
        var callCount = 0
        every { urgencyRepository.findAll() } answers {
            callCount++
            if (callCount < 2) throw RuntimeException("일시적 오류")
            allUrgencies
        }
        coEvery { urgencyAnalyzer.analyze(allUrgencies, title, content) } returns expectedUrgency

        // When
        val result = service.process(articleId, title, content)

        // Then
        assertThat(result).isEqualTo(expectedUrgency)
        verify(exactly = 2) { urgencyRepository.findAll() }
    }
}
