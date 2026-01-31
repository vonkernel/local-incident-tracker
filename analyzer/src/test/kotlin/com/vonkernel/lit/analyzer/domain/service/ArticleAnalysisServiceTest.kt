package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.KeywordAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.RefineArticleAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.TopicAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.entity.Keyword
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.core.entity.RefinedArticle
import com.vonkernel.lit.core.entity.Topic
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.port.repository.AnalysisResultRepository
import io.mockk.coEvery
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

    private val refineArticleAnalyzer: RefineArticleAnalyzer = mockk()
    private val incidentTypeAnalyzer: IncidentTypeAnalyzer = mockk()
    private val urgencyAnalyzer: UrgencyAnalyzer = mockk()
    private val keywordAnalyzer: KeywordAnalyzer = mockk()
    private val topicAnalyzer: TopicAnalyzer = mockk()
    private val locationsExtractor: LocationsExtractor = mockk()
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

    private val testRefinedArticle = RefinedArticle(
        title = "서울 강남구 화재 발생",
        content = "서울 강남구에서 대형 화재가 발생했습니다.",
        summary = "서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다.",
        writtenAt = Instant.parse("2025-01-15T09:30:00Z")
    )

    private val testIncidentTypes = setOf(IncidentType("fire", "산불"))
    private val testUrgency = Urgency("HIGH", 3)
    private val testKeywords = listOf(Keyword("화재", 10), Keyword("대피", 8))
    private val testTopic = Topic("서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다")
    private val testLocation = Location(
        coordinate = Coordinate(37.4979, 126.9270),
        address = Address(RegionType.BJDONG, "11680", "서울 강남구", "서울특별시", "강남구", "역삼동")
    )

    @BeforeEach
    fun setUp() {
        service = ArticleAnalysisService(
            refineArticleAnalyzer, incidentTypeAnalyzer, urgencyAnalyzer,
            keywordAnalyzer, topicAnalyzer, locationsExtractor,
            analysisResultRepository
        )
    }

    private fun setupDefaultAnalyzerMocks(locations: List<Location> = emptyList()) {
        coEvery { refineArticleAnalyzer.analyze(testArticle) } returns testRefinedArticle
        coEvery { incidentTypeAnalyzer.analyze(testRefinedArticle.title, testRefinedArticle.content) } returns testIncidentTypes
        coEvery { urgencyAnalyzer.analyze(testRefinedArticle.title, testRefinedArticle.content) } returns testUrgency
        coEvery { keywordAnalyzer.analyze(testRefinedArticle.summary) } returns testKeywords
        coEvery { topicAnalyzer.analyze(testRefinedArticle.summary) } returns testTopic
        coEvery { locationsExtractor.process(testArticle.articleId, testRefinedArticle.title, testRefinedArticle.content) } returns locations
        every { analysisResultRepository.existsByArticleId(testArticle.articleId) } returns false
        every { analysisResultRepository.save(any()) } answers { firstArg() }
    }

    @Test
    @DisplayName("2단계 파이프라인: refine 후 5개 분석 결과가 AnalysisResult에 올바르게 조합되어 저장된다")
    fun analyze_combinesAllResults() = runTest {
        // Given
        setupDefaultAnalyzerMocks(listOf(testLocation))

        val savedSlot = slot<AnalysisResult>()
        every { analysisResultRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        service.analyze(testArticle)

        // Then
        val saved = savedSlot.captured
        assertThat(saved.articleId).isEqualTo("article-001")
        assertThat(saved.refinedArticle).isEqualTo(testRefinedArticle)
        assertThat(saved.incidentTypes).isEqualTo(testIncidentTypes)
        assertThat(saved.urgency).isEqualTo(testUrgency)
        assertThat(saved.keywords).containsExactly(Keyword("화재", 10), Keyword("대피", 8))
        assertThat(saved.topic).isEqualTo(testTopic)
        assertThat(saved.locations).containsExactly(testLocation)
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
}
