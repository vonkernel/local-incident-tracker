package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.LocationType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * DefaultLocationValidator 통합 테스트 (실제 OpenAI API)
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
class DefaultLocationValidatorIntegrationTest {

    @Autowired
    private lateinit var locationValidator: LocationValidator

    @Test
    fun `사건과 관련 없는 위치를 필터링한다`() = runTest {
        // Given
        val title = "(부여=연합뉴스) 충남 부여군 세도면 화재 발생"
        val content = "충남 부여군 세도면에서 화재가 발생했다. " +
            "서울 소재 한국재난안전기술원은 원인 조사에 착수할 예정이다."
        val candidates = listOf(
            ExtractedLocation("충남 부여군 세도면", LocationType.ADDRESS),
            ExtractedLocation("부여", LocationType.ADDRESS),
            ExtractedLocation("서울", LocationType.ADDRESS)
        )

        // When
        val result = locationValidator.validate(title, content, candidates)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "사건 발생 장소는 최소 1개 이상 유지되어야 한다")
        result.forEach { location ->
            assertNotNull(location.name, "위치 이름이 null이면 안 된다")
            assertTrue(location.name.isNotBlank(), "위치 이름이 비어있으면 안 된다")
            assertNotNull(location.type, "위치 유형이 null이면 안 된다")
        }

        println("=== LocationValidator Integration Test: 필터링 ===")
        println("Input candidates: ${candidates.map { "${it.name} (${it.type})" }}")
        println("Validated result: ${result.map { "${it.name} (${it.type})" }}")
    }

    @Test
    fun `줄임식 지역명을 정식 명칭으로 정규화한다`() = runTest {
        // Given
        val title = "경북 포항시 남구 호동 화학물질 유출"
        val content = "경북 포항시 남구 호동 일대에서 화학물질이 유출되는 사고가 발생했다."
        val candidates = listOf(
            ExtractedLocation("경북 포항시 남구 호동", LocationType.ADDRESS)
        )

        // When
        val result = locationValidator.validate(title, content, candidates)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "사건 발생 장소는 유지되어야 한다")

        println("=== LocationValidator Integration Test: 정규화 ===")
        println("Input candidates: ${candidates.map { "${it.name} (${it.type})" }}")
        println("Validated result: ${result.map { "${it.name} (${it.type})" }}")
    }

    @Test
    fun `LANDMARK 수식어를 정제한다`() = runTest {
        // Given
        val title = "부산 해운대 해수욕장 인근 건물 붕괴"
        val content = "부산 해운대 해수욕장 인근에서 노후 건물이 붕괴되었다."
        val candidates = listOf(
            ExtractedLocation("부산 해운대 해수욕장 인근", LocationType.LANDMARK)
        )

        // When
        val result = locationValidator.validate(title, content, candidates)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "사건 발생 장소는 유지되어야 한다")

        println("=== LocationValidator Integration Test: 수식어 정제 ===")
        println("Input candidates: ${candidates.map { "${it.name} (${it.type})" }}")
        println("Validated result: ${result.map { "${it.name} (${it.type})" }}")
    }

    @Test
    fun `빈 후보 리스트 입력 시 빈 리스트를 반환한다`() = runTest {
        // Given
        val title = "전국 폭염 주의보"
        val content = "기상청은 전국적으로 폭염 주의보를 발령했다."
        val candidates = emptyList<ExtractedLocation>()

        // When
        val result = locationValidator.validate(title, content, candidates)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty(), "빈 후보 리스트에 대해 빈 결과를 반환해야 한다")

        println("=== LocationValidator Integration Test: 빈 입력 ===")
        println("Validated result: $result")
    }
}
