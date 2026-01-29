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
 * DefaultLocationAnalyzer 통합 테스트 (실제 OpenAI API)
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
class DefaultLocationAnalyzerIntegrationTest {

    @Autowired
    private lateinit var locationAnalyzer: LocationAnalyzer

    @Test
    fun `재난 기사에서 위치 정보를 추출한다`() = runTest {
        // Given
        val article = Article(
            articleId = "test-001",
            originId = "origin-001",
            sourceId = "source-001",
            writtenAt = Instant.now(),
            modifiedAt = Instant.now(),
            title = "부산 해운대 해수욕장 인근 건물 붕괴 사고",
            content = "부산광역시 해운대구 중동 해운대 해수욕장 인근의 노후 건물이 붕괴되는 사고가 발생했다. " +
                "사고 현장은 부산 지하철 2호선 해운대역에서 도보 5분 거리에 위치하고 있다. " +
                "부산소방재난본부는 구조대를 긴급 투입하여 매몰자 수색 작업을 진행 중이다."
        )

        // When
        val result = locationAnalyzer.analyze(article)

        // Then - 구조 검증
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "위치 정보가 포함된 기사이므로 최소 1개 이상의 위치가 추출되어야 한다")
        result.forEach { location ->
            assertNotNull(location.name, "위치 이름이 null이면 안 된다")
            assertTrue(location.name.isNotBlank(), "위치 이름이 비어있으면 안 된다")
            assertNotNull(location.type, "위치 유형이 null이면 안 된다")
        }

        // 결과 출력 (수동 확인용)
        println("=== LocationAnalyzer Integration Test Result ===")
        println("Input title: ${article.title}")
        println("Extracted locations:")
        result.forEach { location ->
            println("  - ${location.name} (type: ${location.type})")
        }
    }

    @Test
    fun `위치 정보가 없는 기사에서도 정상 동작한다`() = runTest {
        // Given
        val article = Article(
            articleId = "test-002",
            originId = "origin-002",
            sourceId = "source-002",
            writtenAt = Instant.now(),
            modifiedAt = Instant.now(),
            title = "전국 폭염 주의보 발령",
            content = "기상청은 전국적으로 폭염 주의보를 발령했다. " +
                "낮 최고 기온이 35도 이상으로 올라갈 것으로 예상되며, " +
                "야외 활동 자제와 충분한 수분 섭취를 당부했다."
        )

        // When
        val result = locationAnalyzer.analyze(article)

        // Then
        assertNotNull(result)

        println("=== LocationAnalyzer Test Result (General Article) ===")
        println("Input title: ${article.title}")
        println("Extracted locations: ${result.map { "${it.name} (${it.type})" }}")
    }
}
