package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.KeywordAnalyzer
import com.vonkernel.lit.core.entity.Keyword
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("KeywordsExtractor 테스트")
class KeywordsExtractorTest {

    private val keywordAnalyzer: KeywordAnalyzer = mockk()

    private lateinit var service: KeywordsExtractor

    private val articleId = "article-001"
    private val summary = "서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다."

    private val expectedKeywords = listOf(
        Keyword("화재", 10),
        Keyword("대피", 8),
        Keyword("소방", 6)
    )

    @BeforeEach
    fun setUp() {
        service = KeywordsExtractor(keywordAnalyzer)
    }

    @Test
    @DisplayName("키워드 추출 성공")
    fun `키워드 추출 성공`() = runTest {
        // Given
        coEvery { keywordAnalyzer.analyze(summary) } returns expectedKeywords

        // When
        val result = service.process(articleId, summary)

        // Then
        assertThat(result).isEqualTo(expectedKeywords)
        coVerify(exactly = 1) { keywordAnalyzer.analyze(summary) }
    }

    @Test
    @DisplayName("분석 실패 시 재시도 후 성공")
    fun `분석 실패 시 재시도 후 성공`() = runTest {
        // Given
        coEvery { keywordAnalyzer.analyze(summary) } throws
            RuntimeException("일시적 오류") andThen expectedKeywords

        // When
        val result = service.process(articleId, summary)

        // Then
        assertThat(result).isEqualTo(expectedKeywords)
        coVerify(exactly = 2) { keywordAnalyzer.analyze(summary) }
    }
}
