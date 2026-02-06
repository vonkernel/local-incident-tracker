package com.vonkernel.lit.analyzer.adapter.inbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.DlqEventListener
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.ArticlePayload
import com.vonkernel.lit.analyzer.adapter.inbound.consumer.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.domain.exception.ArticleAnalysisException
import com.vonkernel.lit.analyzer.domain.port.DlqPublisher
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.port.repository.AnalysisResultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

@DisplayName("DlqEventListener 테스트")
class DlqEventListenerTest {

    private val articleAnalysisService: ArticleAnalysisService = mockk()
    private val analysisResultRepository: AnalysisResultRepository = mockk()
    private val dlqPublisher: DlqPublisher = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private val maxRetries = 3

    private lateinit var listener: DlqEventListener

    private val defaultPayload = ArticlePayload(
        articleId = "article-001",
        originId = "origin-001",
        sourceId = "yonhapnews",
        writtenAt = "2025-01-15T09:30:00Z",
        modifiedAt = "2025-01-15T10:00:00Z",
        title = "테스트 기사",
        content = "테스트 본문",
        updatedAt = "2025-01-15T10:00:00Z"
    )

    @BeforeEach
    fun setUp() {
        listener = DlqEventListener(
            articleAnalysisService,
            analysisResultRepository,
            dlqPublisher,
            objectMapper,
            maxRetries
        )
    }

    private fun createRecord(
        value: String,
        retryCount: Int? = null,
        offset: Long = 0L
    ): ConsumerRecord<String, String> {
        val headers = RecordHeaders()
        retryCount?.let {
            headers.add("dlq-retry-count", it.toString().toByteArray())
        }
        return ConsumerRecord(
            "dlq-topic", 0, offset, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
            -1, -1, null, value, headers, Optional.empty()
        )
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 메시지를 폐기한다")
    fun onMaxRetriesExceeded_discardsMessage() {
        // Given
        val record = createRecord("""{}""", retryCount = 3)

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any(), any()) }
        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
    }

    @Test
    @DisplayName("정상 재처리 시 analyze()를 호출한다")
    fun onValidDlqEvent_callsAnalyze() {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = defaultPayload, op = "c")
        val record = createRecord("""{}""", retryCount = 1)
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope
        every { analysisResultRepository.findArticleUpdatedAtByArticleId("article-001") } returns null
        coEvery { articleAnalysisService.analyze(any<Article>(), any()) } returns Unit

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 1) { articleAnalysisService.analyze(any<Article>(), any()) }
    }

    @Test
    @DisplayName("이벤트가 기존 분석보다 오래된 경우 폐기한다")
    fun onOutdatedEvent_discardsMessage() {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = defaultPayload, op = "c")
        val record = createRecord("""{}""", retryCount = 0)
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope
        // 기존 분석 결과의 updatedAt이 이벤트보다 최신
        every { analysisResultRepository.findArticleUpdatedAtByArticleId("article-001") } returns
            Instant.parse("2025-01-15T12:00:00Z")

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any(), any()) }
        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
    }

    @Test
    @DisplayName("재처리 실패 시 retry count를 증가시켜 DLQ에 다시 발행한다")
    fun onReprocessFailure_republishesWithIncrementedRetryCount() {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = defaultPayload, op = "c")
        val rawValue = """{"test":"value"}"""
        val record = createRecord(rawValue, retryCount = 1)
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope
        every { analysisResultRepository.findArticleUpdatedAtByArticleId("article-001") } returns null
        coEvery { articleAnalysisService.analyze(any<Article>(), any()) } throws
            ArticleAnalysisException("article-001", "Analysis failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } returns Unit

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 1) { dlqPublisher.publish(rawValue, 2, "article-001") }
    }

    @Test
    @DisplayName("non-create 이벤트는 무시한다")
    fun onNonCreateEvent_ignoresMessage() {
        // Given
        val envelope = DebeziumEnvelope(before = defaultPayload, after = defaultPayload, op = "u")
        val record = createRecord("""{}""", retryCount = 0)
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any(), any()) }
    }

    @Test
    @DisplayName("retry count 헤더가 없으면 0으로 처리한다")
    fun onMissingRetryCountHeader_treatsAsZero() {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = defaultPayload, op = "c")
        val record = createRecord("""{}""", retryCount = null)
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope
        every { analysisResultRepository.findArticleUpdatedAtByArticleId("article-001") } returns null
        coEvery { articleAnalysisService.analyze(any<Article>(), any()) } returns Unit

        // When
        listener.onDlqEvent(record)

        // Then
        coVerify(exactly = 1) { articleAnalysisService.analyze(any<Article>(), any()) }
    }
}
