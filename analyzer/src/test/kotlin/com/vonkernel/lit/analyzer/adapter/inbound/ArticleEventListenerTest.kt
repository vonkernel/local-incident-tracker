package com.vonkernel.lit.analyzer.adapter.inbound

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.analyzer.adapter.inbound.model.ArticlePayload
import com.vonkernel.lit.analyzer.adapter.inbound.model.DebeziumEnvelope
import com.vonkernel.lit.analyzer.domain.service.ArticleAnalysisService
import com.vonkernel.lit.core.entity.Article
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ArticleEventListener 테스트")
class ArticleEventListenerTest {

    private val articleAnalysisService: ArticleAnalysisService = mockk()
    private val objectMapper: ObjectMapper = mockk()
    private lateinit var listener: ArticleEventListener

    private val defaultPayload = ArticlePayload(
        articleId = "article-001",
        originId = "origin-001",
        sourceId = "yonhapnews",
        writtenAt = "2025-01-15T09:30:00Z",
        modifiedAt = "2025-01-15T10:00:00Z",
        title = "테스트 기사",
        content = "테스트 본문"
    )

    @BeforeEach
    fun setUp() {
        listener = ArticleEventListener(articleAnalysisService, objectMapper)
    }

    @Test
    @DisplayName("op=c 이벤트 수신 시 articleAnalysisService.analyze()를 호출한다")
    fun onCreateEvent_callsAnalyze() = runTest {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = defaultPayload, op = "c")
        val record = ConsumerRecord<String, String>("article-events", 0, 0L, null, """{}""")
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope
        coEvery { articleAnalysisService.analyze(any<Article>()) } returns Unit

        // When
        listener.onArticleEvent(record)

        // Then
        coVerify(exactly = 1) { articleAnalysisService.analyze(any<Article>()) }
    }

    @Test
    @DisplayName("op=u 이벤트 수신 시 analyze()를 호출하지 않는다")
    fun onUpdateEvent_doesNotCallAnalyze() = runTest {
        // Given
        val envelope = DebeziumEnvelope(before = defaultPayload, after = defaultPayload, op = "u")
        val record = ConsumerRecord<String, String>("article-events", 0, 0L, null, """{}""")
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope

        // When
        listener.onArticleEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any()) }
    }

    @Test
    @DisplayName("op=d 이벤트 수신 시 analyze()를 호출하지 않는다")
    fun onDeleteEvent_doesNotCallAnalyze() = runTest {
        // Given
        val envelope = DebeziumEnvelope(before = defaultPayload, after = null, op = "d")
        val record = ConsumerRecord<String, String>("article-events", 0, 0L, null, """{}""")
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope

        // When
        listener.onArticleEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any()) }
    }

    @Test
    @DisplayName("after가 null인 create 이벤트 수신 시 analyze()를 호출하지 않는다")
    fun onCreateEventWithNullAfter_doesNotCallAnalyze() = runTest {
        // Given
        val envelope = DebeziumEnvelope(before = null, after = null, op = "c")
        val record = ConsumerRecord<String, String>("article-events", 0, 0L, null, """{}""")
        every { objectMapper.readValue(record.value(), DebeziumEnvelope::class.java) } returns envelope

        // When
        listener.onArticleEvent(record)

        // Then
        coVerify(exactly = 0) { articleAnalysisService.analyze(any()) }
    }

    @Test
    @DisplayName("JSON 역직렬화 실패 시 예외가 전파된다")
    fun onDeserializationFailure_throwsException() {
        // Given
        val record = ConsumerRecord<String, String>("article-events", 0, 0L, null, "invalid-json")
        every {
            objectMapper.readValue(record.value(), DebeziumEnvelope::class.java)
        } throws com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character")

        // When & Then
        assertThatThrownBy { listener.onArticleEvent(record) }
            .isInstanceOf(com.fasterxml.jackson.core.JsonParseException::class.java)
    }
}
