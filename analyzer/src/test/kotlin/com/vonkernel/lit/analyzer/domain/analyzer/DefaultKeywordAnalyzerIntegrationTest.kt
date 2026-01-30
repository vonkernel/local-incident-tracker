package com.vonkernel.lit.analyzer.domain.analyzer

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

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
    fun `재난 기사 요약에서 키워드를 추출한다`() = runTest {
        // Given
        val summary = "강원도 강릉시 일대에서 산불이 발생하여 강풍을 타고 빠르게 확산되었다. " +
            "산림청은 산불 3단계 대응을 발령하고 헬기 15대를 투입했으며, 인근 주민 500여 명이 긴급 대피했다."

        // When
        val result = keywordAnalyzer.analyze(summary)

        // Then - 구조 검증
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "최소 1개 이상의 키워드가 추출되어야 한다")
        assertTrue(result.size <= 3, "최대 3개의 키워드만 추출되어야 한다")
        result.forEach { keyword ->
            assertNotNull(keyword.keyword, "키워드 텍스트가 null이면 안 된다")
            assertTrue(keyword.keyword.isNotBlank(), "키워드 텍스트가 비어있으면 안 된다")
            assertTrue(keyword.priority > 0, "키워드 우선순위는 양수여야 한다")
        }

        // 결과 출력 (수동 확인용)
        println("=== KeywordAnalyzer Integration Test Result ===")
        println("Input summary: $summary")
        println("Keywords: ${result.map { "${it.keyword} (priority: ${it.priority})" }}")
    }
}
