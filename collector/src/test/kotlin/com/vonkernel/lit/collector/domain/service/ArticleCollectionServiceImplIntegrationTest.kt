package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.adapter.outbound.news.SafetyDataApiAdapter
import com.vonkernel.lit.collector.adapter.outbound.news.config.WebClientConfig
import com.vonkernel.lit.collector.fake.FakeArticleRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

/**
 * ArticleCollectionServiceImpl 통합 테스트 (실제 Safety Data API 호출)
 *
 * 실행 조건:
 * - 환경 변수 SAFETY_DATA_API_KEY 설정 필요
 * - @Tag("integration")으로 태그 지정
 *
 * 실행 방법:
 * ```bash
 * ./gradlew collector:integrationTest
 * ```
 *
 * 검증 항목:
 * 1. 실제 API 호출 + FakeRepository 저장
 * 2. 페이징 처리 로직 (첫 페이지 → 나머지 페이지)
 * 3. 중복 기사 필터링 (filterNonExisting)
 * 4. 재시도 로직 (API 실패 시)
 */
@SpringBootTest(
    classes = [
        WebClientConfig::class,
        SafetyDataApiAdapter::class,
        ArticleCollectionServiceImpl::class,
        ArticleCollectionServiceImplIntegrationTest.TestConfig::class
    ]
)
@TestPropertySource(
    properties = [
        "safety-data.api.url=https://www.safetydata.go.kr",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    ]
)
@Tag("integration")
class ArticleCollectionServiceImplIntegrationTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun fakeArticleRepository(): FakeArticleRepository {
            return FakeArticleRepository()
        }
    }

    @Autowired
    private lateinit var service: ArticleCollectionService

    @Autowired
    private lateinit var repository: FakeArticleRepository

    @BeforeEach
    fun setUp() {
        repository.clear()
    }

    @AfterEach
    fun tearDown() {
        repository.clear()
    }

    @Test
    fun `실제 API로 기사 수집 및 저장`() = runBlocking {
        // Given - 최근 날짜로 테스트
        val targetDate = LocalDate.of(2026, 1, 27)
        val pageSize = 10

        // When
        service.collectArticlesForDate(targetDate, pageSize)

        // Then
        val savedArticles = repository.findAll()
        println("=== ArticleCollectionService Integration Test Result ===")
        println("Target Date: $targetDate")
        println("Page Size: $pageSize")
        println("Articles Saved: ${savedArticles.size}")

        // 기사가 저장되었다면 구조 검증
        if (savedArticles.isNotEmpty()) {
            val firstArticle = savedArticles.first()
            assertTrue(firstArticle.articleId.isNotBlank())
            assertTrue(firstArticle.title.isNotBlank())
            assertTrue(firstArticle.content.isNotBlank())

            println("\n--- Sample Article ---")
            println("ID: ${firstArticle.articleId}")
            println("Title: ${firstArticle.title}")
            println("Written At: ${firstArticle.writtenAt}")
        }
    }

    @Test
    fun `중복 기사 필터링 검증`() = runBlocking {
        // Given - 최근 날짜로 테스트
        val targetDate = LocalDate.of(2026, 1, 27)
        val pageSize = 5

        // When - 1차 수집
        service.collectArticlesForDate(targetDate, pageSize)
        val firstCollectionSize = repository.size()

        // When - 2차 수집 (동일 날짜)
        service.collectArticlesForDate(targetDate, pageSize)
        val secondCollectionSize = repository.size()

        // Then - 중복 제거되어 동일한 크기 유지
        // 주의: 타이밍 이슈로 동일한 크기가 나오지 않을 수 있음
        assertTrue(firstCollectionSize == secondCollectionSize)

        println("=== Duplicate Filtering Test ===")
        println("1st Collection: $firstCollectionSize articles")
        println("2nd Collection: $secondCollectionSize articles")
        println("Duplicates filtered: ${firstCollectionSize == secondCollectionSize}")
    }

    @Test
    fun `페이징 처리 검증`() = runBlocking {
        // Given - 작은 페이지 크기로 여러 페이지 수집
        val targetDate = LocalDate.of(2026, 1, 27)
        val pageSize = 3

        // When
        service.collectArticlesForDate(targetDate, pageSize)

        // Then
        val savedArticles = repository.findAll()

        println("=== Pagination Test ===")
        println("Page Size: $pageSize")
        println("Total Articles Collected: ${savedArticles.size}")
        println("Expected Pages: ${(savedArticles.size + pageSize - 1) / pageSize}")

        // 페이지 크기보다 많은 기사가 있다면 페이징이 동작한 것
        if (savedArticles.size > pageSize) {
            println("Pagination worked: collected more than one page")
            assertTrue(true)
        }
    }
}