package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.CollectionException
import com.vonkernel.lit.collector.domain.model.ArticlePage
import com.vonkernel.lit.collector.domain.port.NewsApiPort
import com.vonkernel.lit.entity.Article
import com.vonkernel.lit.repository.ArticleRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class ArticleCollectionServiceImplTest {

    private lateinit var newsApiPort: NewsApiPort
    private lateinit var articleRepository: ArticleRepository
    private lateinit var service: ArticleCollectionServiceImpl

    @BeforeEach
    fun setUp() {
        newsApiPort = mockk()
        articleRepository = mockk()
        service = ArticleCollectionServiceImpl(newsApiPort, articleRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Test Helpers ==========

    private val testTime = Instant.parse("2024-01-15T10:00:00Z")

    private fun createValidArticle(id: String = "article-1") = Article(
        articleId = id,
        originId = "origin-$id",
        sourceId = "source-1",
        writtenAt = testTime,
        modifiedAt = testTime,
        title = "Test Title",
        content = "Test Content",
        sourceUrl = "https://example.com"
    )

    private fun createInvalidArticle(id: String = "invalid-1") = Article(
        articleId = id,
        originId = "origin-$id",
        sourceId = "source-1",
        writtenAt = testTime,
        modifiedAt = testTime,
        title = "", // Invalid: blank title
        content = "Test Content",
        sourceUrl = null
    )

    private fun createArticlePage(
        articles: List<Article>,
        totalCount: Int,
        pageNo: Int,
        numOfRows: Int = 1000
    ) = ArticlePage(
        articles = articles,
        totalCount = totalCount,
        pageNo = pageNo,
        numOfRows = numOfRows
    )

    // ========== Tests ==========

    @Test
    fun `단일 페이지 수집 성공`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val articles = listOf(createValidArticle("1"), createValidArticle("2"))
        val page = createArticlePage(articles, totalCount = 2, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page
        every { articleRepository.filterNonExisting(any()) } answers { firstArg<List<String>>() }
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        coVerify(exactly = 1) { newsApiPort.fetchArticles("20240115", 1, 2) }
        verify(exactly = 1) { articleRepository.saveAll(articles) }
    }

    @Test
    fun `다중 페이지 수집 성공`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val page1Articles = listOf(createValidArticle("1"), createValidArticle("2"))
        val page2Articles = listOf(createValidArticle("3"), createValidArticle("4"))
        val page1 = createArticlePage(page1Articles, totalCount = 4, pageNo = 1)
        val page2 = createArticlePage(page2Articles, totalCount = 4, pageNo = 2)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page1
        coEvery { newsApiPort.fetchArticles("20240115", 2, pageSize) } returns page2
        every { articleRepository.filterNonExisting(any()) } answers {
            firstArg<List<String>>()
        }
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        coVerify(exactly = 1) { newsApiPort.fetchArticles("20240115", 1, 2) }
        coVerify(exactly = 1) { newsApiPort.fetchArticles("20240115", 2, 2) }
        verify(exactly = 1) { articleRepository.saveAll(page1Articles) }
        verify(exactly = 1) { articleRepository.saveAll(page2Articles) }
    }

    @Test
    fun `검증 실패한 article은 저장하지 않음`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val validArticle = createValidArticle("1")
        val invalidArticle = createInvalidArticle("2")
        val articles = listOf(validArticle, invalidArticle)
        val page = createArticlePage(articles, totalCount = 2, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page
        every { articleRepository.filterNonExisting(listOf("1")) } returns listOf("1")
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        verify(exactly = 1) { articleRepository.saveAll(listOf(validArticle)) }
    }

    @Test
    fun `이미 존재하는 article은 저장하지 않음`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val articles = listOf(createValidArticle("1"), createValidArticle("2"))
        val page = createArticlePage(articles, totalCount = 2, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page
        every { articleRepository.filterNonExisting(listOf("1", "2")) } returns listOf("2") // Only "2" is new
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        verify(exactly = 1) { articleRepository.saveAll(listOf(articles[1])) }
    }

    @Test
    fun `모든 article이 이미 존재하면 저장 호출 안함`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val articles = listOf(createValidArticle("1"), createValidArticle("2"))
        val page = createArticlePage(articles, totalCount = 2, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page
        every { articleRepository.filterNonExisting(listOf("1", "2")) } returns emptyList() // All exist

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        verify(exactly = 0) { articleRepository.saveAll(any()) }
    }

    @Test
    fun `페이지 수집 실패 후 재시도 성공`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val page1Articles = listOf(createValidArticle("1"))
        val page2Articles = listOf(createValidArticle("2"))
        val page1 = createArticlePage(page1Articles, totalCount = 4, pageNo = 1)
        val page2 = createArticlePage(page2Articles, totalCount = 4, pageNo = 2)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page1
        coEvery { newsApiPort.fetchArticles("20240115", 2, pageSize) } throws RuntimeException("Network error") andThen page2
        every { articleRepository.filterNonExisting(any()) } answers { firstArg<List<String>>() }
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        coVerify(exactly = 2) { newsApiPort.fetchArticles("20240115", 2, pageSize) } // Initial + retry
        verify(exactly = 1) { articleRepository.saveAll(page1Articles) }
        verify(exactly = 1) { articleRepository.saveAll(page2Articles) }
    }

    @Test
    fun `재시도 후에도 실패하면 예외 발생`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val page1Articles = listOf(createValidArticle("1"))
        val page1 = createArticlePage(page1Articles, totalCount = 4, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page1
        coEvery { newsApiPort.fetchArticles("20240115", 2, pageSize) } throws RuntimeException("Network error")
        every { articleRepository.filterNonExisting(any()) } answers { firstArg<List<String>>() }
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When & Then
        val exception = assertThrows<CollectionException> {
            service.collectArticlesForDate(date, pageSize)
        }
//        assertEquals(CollectionException::class, exception.cause)

        assertTrue(exception.message?.contains("Failed to collect pages") == true)
        coVerify(atLeast = 2) { newsApiPort.fetchArticles("20240115", 2, pageSize) } // Initial + retry
    }

    @Test
    fun `API 호출 실패 시 지수 백오프로 재시도`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val articles = listOf(createValidArticle("1"))
        val page = createArticlePage(articles, totalCount = 1, pageNo = 1)
        val pageSize = 2

        var attemptCount = 0
        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } answers {
            attemptCount++
            if (attemptCount < 3) throw RuntimeException("Temporary failure")
            else page
        }
        every { articleRepository.filterNonExisting(any()) } returns listOf("1")
        every { articleRepository.saveAll(any()) } answers { firstArg<List<Article>>() }

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        assertEquals(3, attemptCount) // Initial + 2 retries
        verify(exactly = 1) { articleRepository.saveAll(articles) }
    }

    @Test
    fun `최대 재시도 초과 시 예외 발생`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } throws RuntimeException("Permanent failure")

        // When & Then
        val exception = assertThrows<CollectionException> {
            service.collectArticlesForDate(date, pageSize)
        }

        assertTrue(exception.message?.contains("Max retries exceeded during initialization") == true)
        // 1 + 3 retries
        coVerify(exactly = 4) { newsApiPort.fetchArticles("20240115", 1, pageSize) }
    }

    @Test
    fun `빈 페이지 응답 시 저장 호출 안함`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val page = createArticlePage(emptyList(), totalCount = 0, pageNo = 1)
        val pageSize = 2

        coEvery { newsApiPort.fetchArticles("20240115", 1, pageSize) } returns page

        // When
        service.collectArticlesForDate(date, pageSize)

        // Then
        verify(exactly = 0) { articleRepository.saveAll(any()) }
    }
}
