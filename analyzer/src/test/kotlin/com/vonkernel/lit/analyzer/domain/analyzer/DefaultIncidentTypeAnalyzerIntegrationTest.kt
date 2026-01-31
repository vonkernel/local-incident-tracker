package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.port.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.core.port.repository.IncidentTypeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * DefaultIncidentTypeAnalyzer 통합 테스트 (실제 OpenAI API + PostgreSQL)
 *
 * 실행 조건:
 * - 환경 변수 SPRING_AI_OPENAI_API_KEY, DB_URL, DB_USERNAME, DB_PASSWORD 설정 필요
 * - PostgreSQL에 incident_type 테이블 데이터 존재해야 함
 *
 * 실행 방법:
 * ```bash
 * ./gradlew analyzer:integrationTest
 * ```
 */
@SpringBootTest
@Tag("integration")
class DefaultIncidentTypeAnalyzerIntegrationTest {

    @Autowired
    private lateinit var incidentTypeAnalyzer: IncidentTypeAnalyzer

    @Autowired
    private lateinit var incidentTypeRepository: IncidentTypeRepository

    @Test
    fun `재난 기사에서 사건 유형을 분류한다`() = runTest {
        // Given
        val title = "서울 강남구 대형 건물 화재 발생…소방당국 진화 중"
        val content = "서울 강남구 역삼동의 한 대형 상업 건물에서 오늘 오후 2시경 화재가 발생했다. " +
            "소방당국은 소방차 30대와 인력 100여 명을 투입해 진화 작업을 진행 중이다. " +
            "현재까지 인명 피해는 보고되지 않았으나, 건물 내 수백 명이 대피한 것으로 알려졌다."

        // When
        val incidentTypes = incidentTypeRepository.findAll()
        val result = incidentTypeAnalyzer.analyze(incidentTypes, title, content)

        // Then - 구조 검증 (LLM 응답은 예측 불가능하므로 구조만 검증)
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "화재 관련 기사이므로 최소 1개 이상의 사건 유형이 분류되어야 한다")
        result.forEach { incidentType ->
            assertNotNull(incidentType.code, "사건 유형 코드가 null이면 안 된다")
            assertNotNull(incidentType.name, "사건 유형 이름이 null이면 안 된다")
            assertTrue(incidentType.code.isNotBlank(), "사건 유형 코드가 비어있으면 안 된다")
        }

        // 결과 출력 (수동 확인용)
        println("=== IncidentTypeAnalyzer Integration Test Result ===")
        println("Input title: $title")
        println("Classified types: ${result.map { "${it.code}: ${it.name}" }}")
    }

    @Test
    fun `재난과 무관한 기사는 빈 결과를 반환할 수 있다`() = runTest {
        // Given
        val title = "정부, 내년도 예산안 국회 제출"
        val content = "정부가 내년도 예산안을 국회에 제출했다. " +
            "총 규모는 600조 원으로 올해 대비 5% 증가했으며, " +
            "복지·교육·국방 분야에 중점 배분되었다."

        // When
        val incidentTypes = incidentTypeRepository.findAll()
        val result = incidentTypeAnalyzer.analyze(incidentTypes, title, content)

        // Then
        assertNotNull(result)
        // 재난과 무관한 기사는 빈 Set 또는 소수의 결과가 나올 수 있음
        println("=== Non-disaster Article Test Result ===")
        println("Input title: $title")
        println("Classified types: ${result.map { "${it.code}: ${it.name}" }}")
    }
}
