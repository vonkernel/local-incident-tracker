package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationValidator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.geocoding.GeocodingPort
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LocationAnalysisService 테스트")
class LocationAnalysisServiceTest {

    private val locationAnalyzer: LocationAnalyzer = mockk()
    private val locationValidator: LocationValidator = mockk()
    private val geocodingPort: GeocodingPort = mockk()

    private lateinit var service: LocationAnalysisService

    private val articleId = "article-001"
    private val title = "서울 강남구 화재 발생"
    private val content = "서울 강남구에서 대형 화재가 발생했습니다."

    private val testLocation = Location(
        coordinate = Coordinate(37.4979, 126.9270),
        address = Address(RegionType.BJDONG, "11680", "서울 강남구", "서울특별시", "강남구", "역삼동")
    )

    @BeforeEach
    fun setUp() {
        service = LocationAnalysisService(locationAnalyzer, locationValidator, geocodingPort)
    }

    private fun setupDefaultMocks(extractedLocations: List<ExtractedLocation> = emptyList()) {
        coEvery { locationAnalyzer.analyze(title, content) } returns extractedLocations
        coEvery { locationValidator.validate(title, content, extractedLocations) } returns extractedLocations
    }

    @Test
    @DisplayName("ADDRESS 타입 - geocodeByAddress로 해소된다")
    fun analyze_addressType_resolvesViaGeocode() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("서울 강남구", LocationType.ADDRESS))
        setupDefaultMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("서울 강남구") } returns listOf(testLocation)

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        assertThat(result).containsExactly(testLocation)
    }

    @Test
    @DisplayName("ADDRESS 타입 - geocodeByAddress 빈 결과 시 unresolvedLocation 반환")
    fun analyze_addressType_emptyResult_returnsUnresolved() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("강남역", LocationType.ADDRESS))
        setupDefaultMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("강남역") } returns emptyList()

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        coVerify(exactly = 1) { geocodingPort.geocodeByAddress("강남역") }
        coVerify(exactly = 0) { geocodingPort.geocodeByKeyword(any()) }
        assertThat(result).hasSize(1)
        assertThat(result[0].coordinate).isNull()
        assertThat(result[0].address.regionType).isEqualTo(RegionType.UNKNOWN)
        assertThat(result[0].address.addressName).isEqualTo("강남역")
    }

    @Test
    @DisplayName("LANDMARK 타입 - geocodeByKeyword 우선 호출, 빈 결과 시 geocodeByAddress fallback")
    fun analyze_landmarkType_fallbackToAddress() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("남산타워", LocationType.LANDMARK))
        setupDefaultMocks(extracted)
        coEvery { geocodingPort.geocodeByKeyword("남산타워") } returns emptyList()
        coEvery { geocodingPort.geocodeByAddress("남산타워") } returns listOf(testLocation)

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        coVerify(exactly = 1) { geocodingPort.geocodeByKeyword("남산타워") }
        coVerify(exactly = 1) { geocodingPort.geocodeByAddress("남산타워") }
        assertThat(result).containsExactly(testLocation)
    }

    @Test
    @DisplayName("UNRESOLVABLE 타입 - geocoding API 호출 없이 UNKNOWN Location 생성")
    fun analyze_unresolvableType_createsUnknownLocation() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("알 수 없는 장소", LocationType.UNRESOLVABLE))
        setupDefaultMocks(extracted)

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        coVerify(exactly = 0) { geocodingPort.geocodeByAddress(any()) }
        coVerify(exactly = 0) { geocodingPort.geocodeByKeyword(any()) }
        assertThat(result).hasSize(1)
        assertThat(result[0].coordinate).isNull()
        assertThat(result[0].address.regionType).isEqualTo(RegionType.UNKNOWN)
        assertThat(result[0].address.code).isEqualTo("UNKNOWN")
        assertThat(result[0].address.addressName).isEqualTo("알 수 없는 장소")
    }

    @Test
    @DisplayName("여러 ExtractedLocation이 병렬 geocoding 후 flatten된다")
    fun analyze_multipleLocations_parallelGeocoding() = runTest {
        // Given
        val location1 = Location(
            coordinate = Coordinate(37.4979, 126.9270),
            address = Address(RegionType.BJDONG, "11680", "서울 강남구", "서울특별시", "강남구", "역삼동")
        )
        val location2 = Location(
            coordinate = Coordinate(37.5665, 126.9780),
            address = Address(RegionType.HADONG, "11010", "서울 중구", "서울특별시", "중구", "명동")
        )

        val extracted = listOf(
            ExtractedLocation("서울 강남구", LocationType.ADDRESS),
            ExtractedLocation("남산타워", LocationType.LANDMARK),
            ExtractedLocation("미상 지역", LocationType.UNRESOLVABLE)
        )
        setupDefaultMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("서울 강남구") } returns listOf(location1)
        coEvery { geocodingPort.geocodeByKeyword("남산타워") } returns listOf(location2)

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result).anySatisfy { loc ->
            assertThat(loc.address.addressName).isEqualTo("서울 강남구")
        }
        assertThat(result).anySatisfy { loc ->
            assertThat(loc.address.addressName).isEqualTo("서울 중구")
        }
        assertThat(result).anySatisfy { loc ->
            assertThat(loc.address.regionType).isEqualTo(RegionType.UNKNOWN)
            assertThat(loc.address.addressName).isEqualTo("미상 지역")
        }
    }

    @Test
    @DisplayName("LocationValidator가 일부 위치를 필터링하고 정규화하면, 필터링된 결과만 geocoding된다")
    fun analyze_validatorFiltersAndNormalizesLocations() = runTest {
        // Given
        val extracted = listOf(
            ExtractedLocation("충남 부여군", LocationType.ADDRESS),
            ExtractedLocation("부여", LocationType.ADDRESS)
        )
        val validated = listOf(
            ExtractedLocation("충청남도 부여군", LocationType.ADDRESS)
        )
        coEvery { locationAnalyzer.analyze(title, content) } returns extracted
        coEvery { locationValidator.validate(title, content, extracted) } returns validated
        coEvery { geocodingPort.geocodeByAddress("충청남도 부여군") } returns listOf(testLocation)

        // When
        val result = service.analyze(articleId, title, content)

        // Then
        coVerify(exactly = 1) { geocodingPort.geocodeByAddress("충청남도 부여군") }
        coVerify(exactly = 0) { geocodingPort.geocodeByAddress("충남 부여군") }
        coVerify(exactly = 0) { geocodingPort.geocodeByAddress("부여") }
        assertThat(result).containsExactly(testLocation)
    }
}
