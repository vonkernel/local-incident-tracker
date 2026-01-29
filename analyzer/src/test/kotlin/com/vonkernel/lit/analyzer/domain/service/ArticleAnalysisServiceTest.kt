package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.KeywordAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.LocationAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.KeywordAnalysisResult
import com.vonkernel.lit.analyzer.domain.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.GeocodingPort
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.entity.Keyword
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.repository.AnalysisResultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ArticleAnalysisService 테스트")
class ArticleAnalysisServiceTest {

    private val incidentTypeAnalyzer: IncidentTypeAnalyzer = mockk()
    private val urgencyAnalyzer: UrgencyAnalyzer = mockk()
    private val keywordAnalyzer: KeywordAnalyzer = mockk()
    private val locationAnalyzer: LocationAnalyzer = mockk()
    private val geocodingPort: GeocodingPort = mockk()
    private val analysisResultRepository: AnalysisResultRepository = mockk()

    private lateinit var service: ArticleAnalysisService

    private val testArticle = Article(
        articleId = "article-001",
        originId = "origin-001",
        sourceId = "yonhapnews",
        writtenAt = Instant.parse("2025-01-15T09:30:00Z"),
        modifiedAt = Instant.parse("2025-01-15T10:00:00Z"),
        title = "서울 강남구 화재 발생",
        content = "서울 강남구에서 대형 화재가 발생했습니다."
    )

    private val testIncidentTypes = setOf(IncidentType("fire", "산불"))
    private val testUrgency = Urgency("HIGH", 3)
    private val testKeywords = KeywordAnalysisResult(
        topic = "화재",
        keywords = listOf(Keyword("화재", 10), Keyword("대피", 8))
    )
    private val testLocation = Location(
        coordinate = Coordinate(37.4979, 126.9270),
        address = Address(RegionType.BJDONG, "11680", "서울 강남구", "서울특별시", "강남구", "역삼동")
    )

    @BeforeEach
    fun setUp() {
        service = ArticleAnalysisService(
            incidentTypeAnalyzer, urgencyAnalyzer, keywordAnalyzer,
            locationAnalyzer, geocodingPort, analysisResultRepository
        )
    }

    private fun setupDefaultAnalyzerMocks(extractedLocations: List<ExtractedLocation> = emptyList()) {
        coEvery { incidentTypeAnalyzer.analyze(testArticle) } returns testIncidentTypes
        coEvery { urgencyAnalyzer.analyze(testArticle) } returns testUrgency
        coEvery { keywordAnalyzer.analyze(testArticle) } returns testKeywords
        coEvery { locationAnalyzer.analyze(testArticle) } returns extractedLocations
        every { analysisResultRepository.existsByArticleId(testArticle.articleId) } returns false
        every { analysisResultRepository.save(any()) } answers { firstArg() }
    }

    @Test
    @DisplayName("4개 분석기 결과 + geocoding 결과가 AnalysisResult에 올바르게 조합되어 저장된다")
    fun analyze_combinesAllResults() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("서울 강남구", LocationType.ADDRESS))
        setupDefaultAnalyzerMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("서울 강남구") } returns listOf(testLocation)

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        val saved = savedSlot.captured
        assertThat(saved.articleId).isEqualTo("article-001")
        assertThat(saved.incidentTypes).isEqualTo(testIncidentTypes)
        assertThat(saved.urgency).isEqualTo(testUrgency)
        assertThat(saved.keywords).containsExactly(Keyword("화재", 10), Keyword("대피", 8))
        assertThat(saved.locations).containsExactly(testLocation)
    }

    @Test
    @DisplayName("LocationType.ADDRESS - geocodeByAddress 우선 호출, 빈 결과 시 geocodeByKeyword fallback")
    fun analyze_addressType_fallbackToKeyword() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("강남역", LocationType.ADDRESS))
        setupDefaultAnalyzerMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("강남역") } returns emptyList()
        coEvery { geocodingPort.geocodeByKeyword("강남역") } returns listOf(testLocation)

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        coVerify(exactly = 1) { geocodingPort.geocodeByAddress("강남역") }
        coVerify(exactly = 1) { geocodingPort.geocodeByKeyword("강남역") }
        assertThat(savedSlot.captured.locations).containsExactly(testLocation)
    }

    @Test
    @DisplayName("LocationType.LANDMARK - geocodeByKeyword 우선 호출, 빈 결과 시 geocodeByAddress fallback")
    fun analyze_landmarkType_fallbackToAddress() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("남산타워", LocationType.LANDMARK))
        setupDefaultAnalyzerMocks(extracted)
        coEvery { geocodingPort.geocodeByKeyword("남산타워") } returns emptyList()
        coEvery { geocodingPort.geocodeByAddress("남산타워") } returns listOf(testLocation)

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        coVerify(exactly = 1) { geocodingPort.geocodeByKeyword("남산타워") }
        coVerify(exactly = 1) { geocodingPort.geocodeByAddress("남산타워") }
        assertThat(savedSlot.captured.locations).containsExactly(testLocation)
    }

    @Test
    @DisplayName("LocationType.UNRESOLVABLE - geocoding API 호출 없이 UNKNOWN Location 생성")
    fun analyze_unresolvableType_createsUnknownLocation() = runTest {
        // Given
        val extracted = listOf(ExtractedLocation("알 수 없는 장소", LocationType.UNRESOLVABLE))
        setupDefaultAnalyzerMocks(extracted)

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        coVerify(exactly = 0) { geocodingPort.geocodeByAddress(any()) }
        coVerify(exactly = 0) { geocodingPort.geocodeByKeyword(any()) }

        val locations = savedSlot.captured.locations
        assertThat(locations).hasSize(1)
        assertThat(locations[0].coordinate).isNull()
        assertThat(locations[0].address.regionType).isEqualTo(RegionType.UNKNOWN)
        assertThat(locations[0].address.code).isEqualTo("UNKNOWN")
        assertThat(locations[0].address.addressName).isEqualTo("알 수 없는 장소")
    }

    @Test
    @DisplayName("기존 분석 결과 존재 시 삭제 후 재분석한다 (idempotency)")
    fun analyze_existingResult_deletesBeforeReanalysis() = runTest {
        // Given
        setupDefaultAnalyzerMocks()
        every { analysisResultRepository.existsByArticleId(testArticle.articleId) } returns true
        every { analysisResultRepository.deleteByArticleId(testArticle.articleId) } returns Unit

        // When
        service.analyze(testArticle)

        // Then
        verify(exactly = 1) { analysisResultRepository.existsByArticleId(testArticle.articleId) }
        verify(exactly = 1) { analysisResultRepository.deleteByArticleId(testArticle.articleId) }
        verify(exactly = 1) { analysisResultRepository.save(any()) }
    }

    @Test
    @DisplayName("기존 분석 결과 없을 시 삭제 호출하지 않는다")
    fun analyze_noExistingResult_doesNotDelete() = runTest {
        // Given
        setupDefaultAnalyzerMocks()
        every { analysisResultRepository.existsByArticleId(testArticle.articleId) } returns false

        // When
        service.analyze(testArticle)

        // Then
        verify(exactly = 1) { analysisResultRepository.existsByArticleId(testArticle.articleId) }
        verify(exactly = 0) { analysisResultRepository.deleteByArticleId(any()) }
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
        val location3 = Location(
            coordinate = null,
            address = Address(RegionType.UNKNOWN, "UNKNOWN", "알 수 없는 곳")
        )

        val extracted = listOf(
            ExtractedLocation("서울 강남구", LocationType.ADDRESS),
            ExtractedLocation("남산타워", LocationType.LANDMARK),
            ExtractedLocation("미상 지역", LocationType.UNRESOLVABLE)
        )
        setupDefaultAnalyzerMocks(extracted)
        coEvery { geocodingPort.geocodeByAddress("서울 강남구") } returns listOf(location1)
        coEvery { geocodingPort.geocodeByKeyword("남산타워") } returns listOf(location2)

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        val locations = savedSlot.captured.locations
        assertThat(locations).hasSize(3)
        assertThat(locations).anySatisfy { loc ->
            assertThat(loc.address.addressName).isEqualTo("서울 강남구")
        }
        assertThat(locations).anySatisfy { loc ->
            assertThat(loc.address.addressName).isEqualTo("서울 중구")
        }
        assertThat(locations).anySatisfy { loc ->
            assertThat(loc.address.regionType).isEqualTo(RegionType.UNKNOWN)
            assertThat(loc.address.addressName).isEqualTo("미상 지역")
        }
    }
}
