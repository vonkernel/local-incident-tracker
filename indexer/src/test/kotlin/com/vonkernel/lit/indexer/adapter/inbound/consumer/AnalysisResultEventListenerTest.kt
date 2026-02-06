package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        coEvery { articleIndexingService.indexAll(any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        coVerify(exactly = 1) {
            articleIndexingService.indexAll(match { results ->
                results.size == 2 &&
                    results[0].articleId == "2026-01-30-001" &&
                    results[1].articleId == "2026-01-30-002"
            })
        }
    }

    @Test
    fun `ignores non-create events`() {
        val record = ConsumerRecord("topic", 0, 0L, "key", updatePayload)

        listener.onAnalysisResultEvents(listOf(record))

        coVerify(exactly = 0) { articleIndexingService.indexAll(any()) }
    }

    @Test
    fun `skips invalid records and processes valid ones in batch`() {
        val badPayload = "invalid json"
        val badRecord = ConsumerRecord("topic", 0, 0L, "key1", badPayload)
        val goodRecord = ConsumerRecord("topic", 0, 1L, "key2", validPayload)

        coEvery { articleIndexingService.indexAll(any()) } just Runs

        listener.onAnalysisResultEvents(listOf(badRecord, goodRecord))

        coVerify(exactly = 1) {
            articleIndexingService.indexAll(match { results ->
                results.size == 1 && results[0].articleId == "2026-01-30-001"
            })
        }
    }

    @Test
    fun `handles batch indexing failure and publishes to DLQ`() {
        coEvery { articleIndexingService.indexAll(any()) } throws RuntimeException("Batch indexing failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } just Runs
        val record = ConsumerRecord("topic", 0, 0L, "key", validPayload)

        // Should not throw
        listener.onAnalysisResultEvents(listOf(record))

        coVerify(exactly = 1) {
            dlqPublisher.publish(validPayload, 0, "2026-01-30-001")
        }
    }

    @Test
    fun `publishes each failed record to DLQ on batch failure`() {
        coEvery { articleIndexingService.indexAll(any()) } throws RuntimeException("Batch indexing failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        listener.onAnalysisResultEvents(listOf(record1, record2))

        coVerify(exactly = 1) { dlqPublisher.publish(validPayload, 0, "2026-01-30-001") }
        coVerify(exactly = 1) { dlqPublisher.publish(validPayload2, 0, "2026-01-30-002") }
    }

    @Test
    fun `continues publishing to DLQ even if one publish fails`() {
        coEvery { articleIndexingService.indexAll(any()) } throws RuntimeException("Batch indexing failed")
        coEvery { dlqPublisher.publish(validPayload, 0, "2026-01-30-001") } throws RuntimeException("DLQ publish failed")
        coEvery { dlqPublisher.publish(validPayload2, 0, "2026-01-30-002") } just Runs
        val record1 = ConsumerRecord("topic", 0, 0L, "key1", validPayload)
        val record2 = ConsumerRecord("topic", 0, 1L, "key2", validPayload2)

        // Should not throw even if first DLQ publish fails
        listener.onAnalysisResultEvents(listOf(record1, record2))

        coVerify(exactly = 1) { dlqPublisher.publish(validPayload, 0, "2026-01-30-001") }
        coVerify(exactly = 1) { dlqPublisher.publish(validPayload2, 0, "2026-01-30-002") }
    }

    @Test
    fun `does not call indexAll when all records are non-create`() {
        val record = ConsumerRecord("topic", 0, 0L, "key", updatePayload)

        listener.onAnalysisResultEvents(listOf(record))

        coVerify(exactly = 0) { articleIndexingService.indexAll(any()) }
    }
}
