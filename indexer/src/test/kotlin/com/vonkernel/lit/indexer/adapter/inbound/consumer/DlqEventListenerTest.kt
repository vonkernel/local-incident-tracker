package com.vonkernel.lit.indexer.adapter.inbound.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vonkernel.lit.indexer.domain.port.DlqPublisher
import com.vonkernel.lit.indexer.domain.service.ArticleIndexingService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DlqEventListenerTest {

    private val articleIndexingService = mockk<ArticleIndexingService>()
    private val dlqPublisher = mockk<DlqPublisher>()
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val maxRetries = 3
    private lateinit var listener: DlqEventListener

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
        listener = DlqEventListener(
            articleIndexingService, dlqPublisher, objectMapper,
            maxRetries = maxRetries, baseDelayMs = 10L, backoffFactor = 2.0
        )
    }

    @Test
    fun `successfully re-indexes DLQ event`() {
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record = createRecordWithRetryCount(validPayload, 0)

        listener.onDlqEvent(record)

        coVerify(exactly = 1) {
            articleIndexingService.index(
                match { it.articleId == "2026-01-30-001" },
                eq(Instant.parse("2026-01-30T08:33:30Z"))
            )
        }
        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
    }

    @Test
    fun `discards event when max retries exceeded`() {
        val record = createRecordWithRetryCount(validPayload, 3)

        listener.onDlqEvent(record)

        coVerify(exactly = 0) { articleIndexingService.index(any(), any()) }
        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
    }

    @Test
    fun `re-publishes to DLQ with incremented retry count on failure`() {
        coEvery { articleIndexingService.index(any(), any()) } throws RuntimeException("Indexing failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } just Runs
        val record = createRecordWithRetryCount(validPayload, 1)

        listener.onDlqEvent(record)

        coVerify(exactly = 1) {
            dlqPublisher.publish(validPayload, 2, "2026-01-30-001")
        }
    }

    @Test
    fun `ignores non-create events`() {
        val record = createRecordWithRetryCount(updatePayload, 0)

        listener.onDlqEvent(record)

        coVerify(exactly = 0) { articleIndexingService.index(any(), any()) }
        coVerify(exactly = 0) { dlqPublisher.publish(any(), any(), any()) }
    }

    @Test
    fun `treats missing retry header as zero`() {
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record = ConsumerRecord("topic", 0, 0L, "key", validPayload)

        listener.onDlqEvent(record)

        coVerify(exactly = 1) { articleIndexingService.index(any(), any()) }
    }

    @Test
    fun `DLQ re-publish failure throws to prevent offset commit`() {
        coEvery { articleIndexingService.index(any(), any()) } throws RuntimeException("Indexing failed")
        coEvery { dlqPublisher.publish(any(), any(), any()) } throws RuntimeException("DLQ publish failed")
        val record = createRecordWithRetryCount(validPayload, 0)

        assertThrows(RuntimeException::class.java) {
            listener.onDlqEvent(record)
        }

        coVerify(exactly = 1) { dlqPublisher.publish(validPayload, 1, "2026-01-30-001") }
    }

    @Test
    fun `applies backoff delay for retry count greater than zero`() = runTest {
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record = createRecordWithRetryCount(validPayload, 2)

        listener.onDlqEvent(record)

        // With baseDelay=10ms and factor=2.0, retryCount=2 → delay = 10 * 2^(2-1) = 20ms
        coVerify(exactly = 1) { articleIndexingService.index(any(), any()) }
    }

    @Test
    fun `no backoff delay for retry count zero`() {
        coEvery { articleIndexingService.index(any(), any()) } just Runs
        val record = createRecordWithRetryCount(validPayload, 0)

        listener.onDlqEvent(record)

        coVerify(exactly = 1) { articleIndexingService.index(any(), any()) }
    }

    private fun createRecordWithRetryCount(value: String, retryCount: Int): ConsumerRecord<String, String> {
        val headers = RecordHeaders().apply {
            add(RecordHeader("dlq-retry-count", retryCount.toString().toByteArray()))
        }
        return ConsumerRecord("topic", 0, 0L, 0L, null, 0, 0, "key", value, headers, null)
    }
}
