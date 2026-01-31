package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.port.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.port.repository.UrgencyRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * DefaultUrgencyAnalyzer 통합 테스트 (실제 OpenAI API + PostgreSQL)
 *
 * 실행 조건:
 * - 환경 변수 SPRING_AI_OPENAI_API_KEY, DB_URL, DB_USERNAME, DB_PASSWORD 설정 필요
 * - PostgreSQL에 urgency_type 테이블 데이터 존재해야 함
 *
 * 실행 방법:
 * ```bash
 * ./gradlew analyzer:integrationTest
 * ```
 */
@SpringBootTest
@Tag("integration")
class DefaultUrgencyAnalyzerIntegrationTest {

    @Autowired
    private lateinit var urgencyAnalyzer: UrgencyAnalyzer

    @Autowired
    private lateinit var urgencyRepository: UrgencyRepository

    @Test
    fun `긴급 재난 기사의 긴급도를 높게 평가한다`() = runTest {
        // Given
        val title = "경북 포항 규모 5.4 지진 발생…건물 붕괴·부상자 다수"
        val content = "오늘 오후 경북 포항시 북구에서 규모 5.4의 지진이 발생했다. " +
            "지진으로 인해 다수의 건물이 붕괴되었으며, 현재까지 부상자 50여 명이 보고되었다. " +
            "여진이 계속되고 있어 주민 대피가 진행 중이다."

        // When
        val urgencies = urgencyRepository.findAll()
        val result = urgencyAnalyzer.analyze(urgencies, title, content)

        // Then - 구조 검증
        assertNotNull(result)
        assertNotNull(result.name, "긴급도 이름이 null이면 안 된다")
        assertTrue(result.name.isNotBlank(), "긴급도 이름이 비어있으면 안 된다")
        assertTrue(result.level > 0, "긴급도 레벨은 양수여야 한다")

        // 결과 출력 (수동 확인용)
        println("=== UrgencyAnalyzer Integration Test Result (High Urgency) ===")
        println("Input title: $title")
        println("Urgency: ${result.name} (level: ${result.level})")
    }

    @Test
    fun `일반 정보성 기사의 긴급도를 낮게 평가한다`() = runTest {
        // Given
        val title = "소방청, 올해 화재 예방 캠페인 실시"
        val content = "소방청이 올해 가을철 화재 예방 캠페인을 전국적으로 실시한다고 밝혔다. " +
            "이번 캠페인은 주거지역 전기 안전 점검과 소화기 사용법 교육을 중심으로 진행된다."

        // When
        val urgencies = urgencyRepository.findAll()
        val result = urgencyAnalyzer.analyze(urgencies, title, content)

        // Then
        assertNotNull(result)
        assertNotNull(result.name)
        assertTrue(result.name.isNotBlank())

        println("=== UrgencyAnalyzer Integration Test Result (Low Urgency) ===")
        println("Input title: $title")
        println("Urgency: ${result.name} (level: ${result.level})")
    }
}
