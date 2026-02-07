package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vonkernel.lit.indexer.domain.exception.BulkIndexingPartialFailureException
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AnalysisResultEventListenerTest {

    private val articleIndexingService = mockk<ArticleIndexingService>()
    private val dlqPublisher = mockk<DlqPublisher>()
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private lateinit var listener: AnalysisResultEventListener

    private val validPayload = """
        {
          "before": null,
          "after": {
            "id": 1,
            "article_id": "2026-01-30-001",
            "payload": "{\"articleId\":\"2026-01-30-001\",\"refinedArticle\":{\"title\":\"테스트\",\"content\":\"본문\",\"summary\":\"요약\",\"writtenAt\":\"2026-01-30T03:21:55Z\"},\"incidentTypes\":[{\"code\":\"FIRE\",\"name\":\"화재\"}],\"urgency\":{\"name\":\"긴급\",\"level\":8},\"keywords\":[{\"keyword\":\"화재\",\"priority\":10}],\"topic\":{\"topic\":\"화재\"},\"locations\":[]}",
            "created_at": "2026-01-30T08:33:30Z"
          },
          "op": "c",
          "source": {"table": "analysis_result_outbox", "connector": "postgresql"}
        }
    """.trimIndent()

    private val validPayload2 = """
        {
          "before": null,
          "after": {
            "id": 2,
            "article_id": "2026-01-30-002",
            "payload": "{\"articleId\":\"2026-01-30-002\",\"refinedArticle\":{\"title\":\"테스트2\",\"content\":\"본문2\",\"summary\":\"요약2\",\"writtenAt\":\"2026-01-30T04:00:00Z\"},\"incidentTypes\":[{\"code\":\"FLOOD\",\"name\":\"홍수\"}],\"urgency\":{\"name\":\"보통\",\"level\":5},\"keywords\":[{\"keyword\":\"홍수\",\"priority\":8}],\"topic\":{\"topic\":\"홍수\"},\"locations\":[]}",
            "created_at": "2026-01-30T09:00:00Z"
          },
          "op": "c",
          "source": {"table": "analysis_result_outbox", "connector": "postgresql"}
        }
    """.trimIndent()

    private val updatePayload = """
        {
          "before": null,
          "after": null,
          "op": "u",
          "source": {"table": "analysis_result_outbox"}
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        listener = AnalysisResultEventListener(articleIndexingService, dlqPublisher, objectMapper)
    }

    @Test
    fun `processes CREATE events as batch`() {
        coEvery { articleIndexingService.indexAll(any(), any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        coVerify(exactly = 1) {
            articleIndexingService.indexAll(
                match { results ->
                    results.size == 2 &&
                        results[0].articleId == "2026-01-30-001" &&
                        results[1].articleId == "2026-01-30-002"
                },
                match { analyzedAts ->
                    analyzedAts.size == 2 &&
                        analyzedAts[0] == Instant.parse("2026-01-30T08:33:30Z") &&
                        analyzedAts[1] == Instant.parse("2026-01-30T09:00:00Z")
                }
            )
        }
    }

    @Test
    fun `ignores non-create events`() {
        val record = ConsumerRecord("topic", 0, 0L, "key", updatePayload)

        listener.onAnalysisResultEvents(listOf(record))

        coVerify(exactly = 0) { articleIndexingService.indexAll(any(), any()) }
    }

    @Test
    fun `publishes invalid records to DLQ and processes valid ones in batch`() {
        val badPayload = "invalid json"
        val badRecord = ConsumerRecord("topic", 0, 0L, "key1", badPayload)
        val goodRecord = ConsumerRecord("topic", 0, 1L, "key2", validPayload)

        coEvery { dlqPublisher.publish(badPayload, 0, null) } just Runs
        coEvery { articleIndexingService.indexAll(any(), any()) } just Runs

        listener.onAnalysisResultEvents(listOf(badRecord, goodRecord))

        coVerify(exactly = 1) { dlqPublisher.publish(badPayload, 0, null) }
        coVerify(exactly = 1) {
            articleIndexingService.indexAll(
                match { results -> results.size == 1 && results[0].articleId == "2026-01-30-001" },
                any()
            )
        }
    }

    @Test
    fun `batch failure triggers per-record fallback then DLQ for still-failing records`() {
        coEvery { articleIndexingService.indexAll(any(), any()) } throws RuntimeException("Batch indexing failed")
        coEvery { articleIndexingService.index(match { it.articleId == "2026-01-30-001" }, any()) } throws RuntimeException("Still failing")
        coEvery { articleIndexingService.index(match { it.articleId == "2026-01-30-002" }, any()) } just Runs
        coEvery { dlqPublisher.publish(any(), any(), any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        // Only the still-failing record goes to DLQ
        coVerify(exactly = 1) { dlqPublisher.publish(validPayload, 0, "2026-01-30-001") }
        coVerify(exactly = 0) { dlqPublisher.publish(validPayload2, 0, "2026-01-30-002") }
    }

    @Test
    fun `batch failure with all per-record fallbacks succeeding sends nothing to DLQ`() {
        coEvery { articleIndexingService.indexAll(any(), any()) } throws RuntimeException("Batch failed")
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
        coVerify(exactly = 2) { articleIndexingService.index(any(), any()) }
    }

    @Test
    fun `partial bulk failure retries only failed articleIds individually`() {
        val partialException = BulkIndexingPartialFailureException(
            failedArticleIds = listOf("2026-01-30-002"),
            message = "Partial failure"
        )
        coEvery { articleIndexingService.indexAll(any(), any()) } throws partialException
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        // Only record2 (the failed one) should be retried individually
        coVerify(exactly = 1) {
            articleIndexingService.index(match { it.articleId == "2026-01-30-002" }, any())
        }
        coVerify(exactly = 0) {
            articleIndexingService.index(match { it.articleId == "2026-01-30-001" }, any())
        }
    }

    @Test
    fun `DLQ publish failure throws to prevent offset commit`() {
        coEvery { articleIndexingService.indexAll(any(), any()) } throws RuntimeException("Batch failed")
        coEvery { articleIndexingService.index(any(), any()) } throws RuntimeException("Per-record failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } throws RuntimeException("DLQ publish failed")
        val record = ConsumerRecord("topic", 0, 0L, "key", validPayload)

        assertThrows(RuntimeException::class.java) {
            listener.onAnalysisResultEvents(listOf(record))
        }
    }

    @Test
    fun `deserialization failure publishes raw record to DLQ`() {
        val badPayload = "invalid json"
        val badRecord = ConsumerRecord("topic", 0, 0L, "key1", badPayload)

        coEvery { dlqPublisher.publish(badPayload, 0, null) } just Runs

        listener.onAnalysisResultEvents(listOf(badRecord))

        coVerify(exactly = 1) { dlqPublisher.publish(badPayload, 0, null) }
        coVerify(exactly = 0) { articleIndexingService.indexAll(any(), any()) }
    }

    @Test
    fun `deserialization DLQ publish failure throws to prevent offset commit`() {
        val badPayload = "invalid json"
        val badRecord = ConsumerRecord("topic", 0, 0L, "key1", badPayload)

        coEvery { dlqPublisher.publish(badPayload, 0, null) } throws RuntimeException("DLQ publish failed")

        assertThrows(RuntimeException::class.java) {
            listener.onAnalysisResultEvents(listOf(badRecord))
        }
    }

    @Test
    fun `does not call indexAll when all records are non-create`() {
        val record = ConsumerRecord("topic", 0, 0L, "key", updatePayload)

        listener.onAnalysisResultEvents(listOf(record))

        coVerify(exactly = 0) { articleIndexingService.indexAll(any(), any()) }
    }
}
