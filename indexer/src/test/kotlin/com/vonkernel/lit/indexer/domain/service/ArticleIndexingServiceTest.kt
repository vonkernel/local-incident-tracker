package com.vonkernel.lit.indexer.domain.service

import com.vonkernel.lit.core.entity.*
import com.vonkernel.lit.indexer.domain.exception.ArticleIndexingException
import com.vonkernel.lit.indexer.domain.port.Embedder
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ArticleIndexingServiceTest {

    private val embedder = mockk<Embedder>()
    private val articleIndexer = mockk<ArticleIndexer>()
    private lateinit var service: ArticleIndexingService

    private val sampleAnalysisResult = AnalysisResult(
        articleId = "test-article-001",
        refinedArticle = RefinedArticle(
            title = "테스트 기사",
            content = "테스트 기사 본문입니다.",
            summary = "테스트 요약",
            writtenAt = Instant.parse("2026-01-30T03:21:55Z")
        ),
        incidentTypes = setOf(IncidentType(code = "FIRE", name = "화재")),
        urgency = Urgency(name = "긴급", level = 8),
        keywords = listOf(Keyword(keyword = "화재", priority = 10)),
        topic = Topic(topic = "화재 사고"),
        locations = listOf(
            Location(
                coordinate = Coordinate(lat = 37.5665, lon = 126.9780),
                address = Address(
                    regionType = RegionType.BJDONG,
                    code = "1111000000",
                    addressName = "서울특별시"
                )
            )
        )
    )

    private val sampleAnalysisResult2 = AnalysisResult(
        articleId = "test-article-002",
        refinedArticle = RefinedArticle(
            title = "테스트 기사 2",
            content = "두 번째 테스트 기사 본문입니다.",
            summary = "테스트 요약 2",
            writtenAt = Instant.parse("2026-01-30T04:00:00Z")
        ),
        incidentTypes = setOf(IncidentType(code = "FLOOD", name = "홍수")),
        urgency = Urgency(name = "보통", level = 5),
        keywords = listOf(Keyword(keyword = "홍수", priority = 8)),
        topic = Topic(topic = "홍수 사고"),
        locations = emptyList()
    )

    @BeforeEach
    fun setUp() {
        service = ArticleIndexingService(embedder, articleIndexer, maxRetries = 2, baseDelayMs = 10L)
    }

    @Test
    fun `index는 임베딩과 함께 문서를 조립하여 인덱싱한다`() = runTest {
        val embeddingBytes = byteArrayOf(1, 2, 3, 4)
        coEvery { embedder.embed(any()) } returns embeddingBytes
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { articleIndexer.index(any()) } just Runs

        service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z"))

        coVerify {
            articleIndexer.index(match { doc ->
                doc.articleId == "test-article-001" &&
                    doc.contentEmbedding.contentEquals(embeddingBytes) &&
                    doc.title == "테스트 기사"
            })
        }
    }

    @Test
    fun `임베더 실패 시 null 임베딩으로 인덱싱 진행`() = runTest {
        coEvery { embedder.embed(any()) } throws RuntimeException("OpenAI API error")
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { articleIndexer.index(any()) } just Runs

        service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z"))

        coVerify {
            articleIndexer.index(match { doc ->
                doc.articleId == "test-article-001" &&
                    doc.contentEmbedding == null
            })
        }
    }

    @Test
    fun `인덱서 실패 시 ArticleIndexingException 발생`() = runTest {
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { articleIndexer.index(any()) } throws RuntimeException("OpenSearch connection refused")

        val exception = assertThrows(ArticleIndexingException::class.java) {
            kotlinx.coroutines.runBlocking { service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z")) }
        }

        assertEquals("test-article-001", exception.articleId)
        assertTrue(exception.message!!.contains("test-article-001"))
    }

    @Test
    fun `기존 문서가 더 최신이면 이벤트 건너뜀`() = runTest {
        val existingModifiedAt = Instant.parse("2026-01-30T10:00:00Z")
        val eventAnalyzedAt = Instant.parse("2026-01-30T08:33:30Z")
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-001") } returns existingModifiedAt

        service.index(sampleAnalysisResult, eventAnalyzedAt)

        coVerify(exactly = 0) { embedder.embed(any()) }
        coVerify(exactly = 0) { articleIndexer.index(any()) }
    }

    @Test
    fun `기존 문서와 타임스탬프가 같으면 이벤트 건너뜀`() = runTest {
        val timestamp = Instant.parse("2026-01-30T08:33:30Z")
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-001") } returns timestamp

        service.index(sampleAnalysisResult, timestamp)

        coVerify(exactly = 0) { embedder.embed(any()) }
        coVerify(exactly = 0) { articleIndexer.index(any()) }
    }

    @Test
    fun `기존 문서가 더 오래되면 인덱싱 진행`() = runTest {
        val existingModifiedAt = Instant.parse("2026-01-30T06:00:00Z")
        val eventAnalyzedAt = Instant.parse("2026-01-30T08:33:30Z")
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-001") } returns existingModifiedAt
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.index(any()) } just Runs

        service.index(sampleAnalysisResult, eventAnalyzedAt)

        coVerify(exactly = 1) { articleIndexer.index(any()) }
    }

    @Test
    fun `analyzedAt 없으면 신선도 검사 생략하고 인덱싱 진행`() = runTest {
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.index(any()) } just Runs

        service.index(sampleAnalysisResult)

        coVerify(exactly = 0) { articleIndexer.findModifiedAtByArticleId(any()) }
        coVerify(exactly = 1) { articleIndexer.index(any()) }
    }

    @Test
    fun `신선도 검사 실패 시 인덱싱 진행`() = runTest {
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } throws RuntimeException("OpenSearch unavailable")
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.index(any()) } just Runs

        service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z"))

        coVerify(exactly = 1) { articleIndexer.index(any()) }
    }

    @Test
    fun `indexAll은 배치 임베딩으로 문서를 조립하여 인덱싱한다`() = runTest {
        val embedding1 = byteArrayOf(1, 2, 3, 4)
        val embedding2 = byteArrayOf(5, 6, 7, 8)
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { embedder.embedAll(any()) } returns listOf(embedding1, embedding2)
        coEvery { articleIndexer.indexAll(any()) } just Runs

        val analyzedAts = listOf(Instant.parse("2026-01-30T08:33:30Z"), Instant.parse("2026-01-30T09:00:00Z"))
        service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2), analyzedAts)

        coVerify {
            articleIndexer.indexAll(match { docs ->
                docs.size == 2 &&
                    docs[0].articleId == "test-article-001" &&
                    docs[0].contentEmbedding.contentEquals(embedding1) &&
                    docs[1].articleId == "test-article-002" &&
                    docs[1].contentEmbedding.contentEquals(embedding2)
            })
        }
    }

    @Test
    fun `배치 임베딩 실패 시 null 임베딩으로 인덱싱 진행`() = runTest {
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { embedder.embedAll(any()) } throws RuntimeException("OpenAI API error")
        coEvery { articleIndexer.indexAll(any()) } just Runs

        service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2))

        coVerify {
            articleIndexer.indexAll(match { docs ->
                docs.size == 2 &&
                    docs[0].contentEmbedding == null &&
                    docs[1].contentEmbedding == null
            })
        }
    }

    @Test
    fun `인덱서 실패 시 예외 전파`() = runTest {
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { embedder.embedAll(any()) } returns listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        coEvery { articleIndexer.indexAll(any()) } throws RuntimeException("OpenSearch bulk error")

        val analyzedAts = listOf(Instant.parse("2026-01-30T08:33:30Z"), Instant.parse("2026-01-30T09:00:00Z"))
        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2), analyzedAts)
            }
        }
    }

    @Test
    fun `빈 리스트에 대해 아무 작업도 하지 않음`() = runTest {
        service.indexAll(emptyList())

        coVerify(exactly = 0) { embedder.embedAll(any()) }
        coVerify(exactly = 0) { articleIndexer.indexAll(any()) }
    }

    @Test
    fun `배치에서 오래된 이벤트 필터링`() = runTest {
        val existingModifiedAt = Instant.parse("2026-01-30T10:00:00Z")
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-001") } returns existingModifiedAt
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-002") } returns null
        coEvery { embedder.embedAll(any()) } returns listOf(byteArrayOf(5, 6, 7, 8))
        coEvery { articleIndexer.indexAll(any()) } just Runs

        val analyzedAts = listOf(Instant.parse("2026-01-30T08:33:30Z"), Instant.parse("2026-01-30T09:00:00Z"))
        service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2), analyzedAts)

        coVerify {
            articleIndexer.indexAll(match { docs ->
                docs.size == 1 && docs[0].articleId == "test-article-002"
            })
        }
    }

    @Test
    fun `모든 이벤트가 오래되면 전부 건너뜀`() = runTest {
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-001") } returns Instant.parse("2026-01-30T10:00:00Z")
        coEvery { articleIndexer.findModifiedAtByArticleId("test-article-002") } returns Instant.parse("2026-01-30T10:00:00Z")

        val analyzedAts = listOf(Instant.parse("2026-01-30T08:33:30Z"), Instant.parse("2026-01-30T09:00:00Z"))
        service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2), analyzedAts)

        coVerify(exactly = 0) { embedder.embedAll(any()) }
        coVerify(exactly = 0) { articleIndexer.indexAll(any()) }
    }

    @Test
    fun `일시적 실패 시 재시도 후 성공`() = runTest {
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { articleIndexer.index(any()) } throws RuntimeException("transient") andThen Unit

        service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z"))

        coVerify(exactly = 2) { articleIndexer.index(any()) }
    }

    @Test
    fun `재시도 소진 후 예외 발생`() = runTest {
        coEvery { embedder.embed(any()) } returns byteArrayOf(1, 2, 3, 4)
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { articleIndexer.index(any()) } throws RuntimeException("persistent failure")

        val exception = assertThrows(ArticleIndexingException::class.java) {
            kotlinx.coroutines.runBlocking { service.index(sampleAnalysisResult, Instant.parse("2026-01-30T08:33:30Z")) }
        }

        assertEquals("test-article-001", exception.articleId)
        // 1 initial + 2 retries = 3 calls
        coVerify(exactly = 3) { articleIndexer.index(any()) }
    }

    @Test
    fun `indexAll 일시적 실패 시 재시도 후 성공`() = runTest {
        coEvery { articleIndexer.findModifiedAtByArticleId(any()) } returns null
        coEvery { embedder.embedAll(any()) } returns listOf(byteArrayOf(1, 2), byteArrayOf(3, 4))
        coEvery { articleIndexer.indexAll(any()) } throws RuntimeException("transient") andThen Unit

        val analyzedAts = listOf(Instant.parse("2026-01-30T08:33:30Z"), Instant.parse("2026-01-30T09:00:00Z"))
        service.indexAll(listOf(sampleAnalysisResult, sampleAnalysisResult2), analyzedAts)

        coVerify(exactly = 2) { articleIndexer.indexAll(any()) }
    }
}
