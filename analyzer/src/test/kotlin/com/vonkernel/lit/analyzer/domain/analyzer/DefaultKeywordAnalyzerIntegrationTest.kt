package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.Article
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * DefaultKeywordAnalyzer 통합 테스트 (실제 OpenAI API)
 *
 * 실행 조건:
 * - 환경 변수 SPRING_AI_OPENAI_API_KEY 설정 필요
 *
 * 실행 방법:
 * ```bash
 * ./gradlew analyzer:integrationTest
 * ```
 */
@SpringBootTest
@Tag("integration")
class DefaultKeywordAnalyzerIntegrationTest {

    @Autowired
    private lateinit var keywordAnalyzer: KeywordAnalyzer

    @Test
    fun `재난 기사에서 키워드를 추출한다`() = runTest {
        // Given
        val article = Article(
            articleId = "test-001",
            originId = "origin-001",
            sourceId = "source-001",
            writtenAt = Instant.now(),
            modifiedAt = Instant.now(),
            title = "강원 산불 확산…주민 500명 긴급 대피",
            content = "강원도 강릉시 일대에서 발생한 산불이 강풍을 타고 빠르게 확산되고 있다. " +
                "산림청은 산불 3단계 대응을 발령하고 헬기 15대를 투입했다. " +
                "인근 마을 주민 500여 명이 긴급 대피했으며, 산림 피해 면적은 약 200ha에 달한다."
        )

        // When
        val result = keywordAnalyzer.analyze(article)

        // Then - 구조 검증
        assertNotNull(result)
        assertNotNull(result.topic, "주제가 null이면 안 된다")
        assertTrue(result.topic.isNotBlank(), "주제가 비어있으면 안 된다")
        assertNotNull(result.keywords, "키워드 목록이 null이면 안 된다")
        assertTrue(result.keywords.isNotEmpty(), "최소 1개 이상의 키워드가 추출되어야 한다")
        result.keywords.forEach { keyword ->
            assertNotNull(keyword.keyword, "키워드 텍스트가 null이면 안 된다")
            assertTrue(keyword.keyword.isNotBlank(), "키워드 텍스트가 비어있으면 안 된다")
            assertTrue(keyword.priority > 0, "키워드 우선순위는 양수여야 한다")
        }

        // 결과 출력 (수동 확인용)
        println("=== KeywordAnalyzer Integration Test Result ===")
        println("Input title: ${article.title}")
        println("Topic: ${result.topic}")
        println("Keywords: ${result.keywords.map { "${it.keyword} (priority: ${it.priority})" }}")
    }
}
