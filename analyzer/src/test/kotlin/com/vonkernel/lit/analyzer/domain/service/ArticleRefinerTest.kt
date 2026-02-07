package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.RefineArticleAnalyzer
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.RefinedArticle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ArticleRefiner 테스트")
class ArticleRefinerTest {

    private val refineArticleAnalyzer: RefineArticleAnalyzer = mockk()

    private lateinit var service: ArticleRefiner

    private val testArticle = Article(
        articleId = "article-001",
        originId = "origin-001",
        sourceId = "yonhapnews",
        writtenAt = Instant.parse("2025-01-15T09:30:00Z"),
        modifiedAt = Instant.parse("2025-01-15T10:00:00Z"),
        title = "서울 강남구 화재 발생",
        content = "서울 강남구에서 대형 화재가 발생했습니다."
    )

    private val testRefinedArticle = RefinedArticle(
        title = "서울 강남구 화재 발생",
        content = "서울 강남구에서 대형 화재가 발생했습니다.",
        summary = "서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다.",
        writtenAt = Instant.parse("2025-01-15T09:30:00Z")
    )

    @BeforeEach
    fun setUp() {
        service = ArticleRefiner(refineArticleAnalyzer)
    }

    @Test
    @DisplayName("분석 성공 시 RefinedArticle 반환")
    fun `분석 성공 시 RefinedArticle 반환`() = runTest {
        // Given
        coEvery { refineArticleAnalyzer.analyze(testArticle) } returns testRefinedArticle

        // When
        val result = service.process(testArticle)

        // Then
        assertThat(result).isEqualTo(testRefinedArticle)
        coVerify(exactly = 1) { refineArticleAnalyzer.analyze(testArticle) }
    }

    @Test
    @DisplayName("분석 실패 시 재시도 후 성공")
    fun `분석 실패 시 재시도 후 성공`() = runTest {
        // Given
        coEvery { refineArticleAnalyzer.analyze(testArticle) } throws
            RuntimeException("일시적 오류") andThen testRefinedArticle

        // When
        val result = service.process(testArticle)

        // Then
        assertThat(result).isEqualTo(testRefinedArticle)
        coVerify(exactly = 2) { refineArticleAnalyzer.analyze(testArticle) }
    }
}
