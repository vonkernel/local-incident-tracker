package com.vonkernel.lit.collector.adapter.outbound

import com.vonkernel.lit.collector.adapter.outbound.news.SafetyDataApiAdapter
import com.vonkernel.lit.collector.adapter.outbound.news.config.WebClientConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * SafetyDataApiAdapter 통합 테스트 (실제 Safety Data API 호출)
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
 * 1. 실제 Safety Data API 호출
 * 2. JSON 응답 파싱 (SafetyDataApiResponse → ArticlePage)
 * 3. 페이징 메타데이터 검증
 * 4. YonhapnewsArticle → Article 변환
 */
@SpringBootTest(
    classes = [
        WebClientConfig::class,
        SafetyDataApiAdapter::class,
    ]
)
@TestPropertySource(
    properties = [
        "safety-data.api.url=https://www.safetydata.go.kr",
    ]
)
@Tag("integration")
class SafetyDataApiAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: SafetyDataApiAdapter

    @Test
    fun `실제 Safety Data API로 기사 조회`() = runBlocking {
        // Given - 최근 날짜로 테스트 (예: 2025년 1월 27일)
        val inqDt = "20250127"
        val pageNo = 1
        val numOfRows = 10

        // When
        val result = adapter.fetchArticles(inqDt, pageNo, numOfRows)

        // Then - 구조 검증
        assertNotNull(result)
        assertTrue(result.pageNo == pageNo)
        assertTrue(result.numOfRows == numOfRows)
        assertTrue(result.totalCount >= 0)

        // 기사 존재 시 변환 검증
        if (result.articles.isNotEmpty()) {
            val firstArticle = result.articles.first()
            assertNotNull(firstArticle.articleId)
            assertNotNull(firstArticle.originId)
            assertNotNull(firstArticle.sourceId)
            assertNotNull(firstArticle.title)
            assertNotNull(firstArticle.content)
            assertNotNull(firstArticle.writtenAt)
            assertNotNull(firstArticle.modifiedAt)
        }

        // 결과 출력 (수동 확인용)
        println("=== SafetyDataApiAdapter Integration Test Result ===")
        println("Query Date: $inqDt")
        println("Total Count: ${result.totalCount}")
        println("Page: ${result.pageNo}/${(result.totalCount + result.numOfRows - 1) / result.numOfRows}")
        println("Articles Retrieved: ${result.articles.size}")

        if (result.articles.isNotEmpty()) {
            println("\n--- First Article ---")
            val first = result.articles.first()
            println("ID: ${first.articleId}")
            println("Title: ${first.title}")
            println("Written At: ${first.writtenAt}")
        }
    }

    @Test
    fun `페이징 메타데이터 검증`() = runBlocking {
        // Given
        val inqDt = "20250127"
        val pageNo = 1
        val numOfRows = 5

        // When
        val result = adapter.fetchArticles(inqDt, pageNo, numOfRows)

        // Then
        assertTrue(result.pageNo == pageNo)
        assertTrue(result.numOfRows == numOfRows)
        assertTrue(result.articles.size <= numOfRows) // API는 요청한 것보다 적게 반환 가능

        println("=== Pagination Metadata Test ===")
        println("Requested: pageNo=$pageNo, numOfRows=$numOfRows")
        println("Response: pageNo=${result.pageNo}, numOfRows=${result.numOfRows}")
        println("Actual articles: ${result.articles.size}")
        println("Total count: ${result.totalCount}")
    }
}