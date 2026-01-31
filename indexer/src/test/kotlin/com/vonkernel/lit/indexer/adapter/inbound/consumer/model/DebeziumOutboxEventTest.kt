package com.vonkernel.lit.indexer.adapter.inbound.consumer.model

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class DebeziumOutboxEventTest {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val cdcEventJson = """
        {
          "before": null,
          "after": {
            "id": 38,
            "article_id": "2026-01-30-4903",
            "payload": "{\"topic\":{\"topic\":\"경북 청도군 열차사고\"},\"urgency\":{\"name\":\"심각\",\"level\":7},\"keywords\":[{\"keyword\":\"열차사고\",\"priority\":10}],\"articleId\":\"2026-01-30-4903\",\"locations\":[{\"address\":{\"code\":\"4782000000\",\"depth1Name\":\"경북\",\"depth2Name\":\"청도군\",\"depth3Name\":null,\"regionType\":\"HADONG\",\"addressName\":\"경상북도 청도군\"},\"coordinate\":{\"lat\":35.647,\"lon\":128.734}}],\"incidentTypes\":[{\"code\":\"TRAFFIC_ACCIDENT\",\"name\":\"교통사고\"}],\"refinedArticle\":{\"title\":\"경부선 열차사고 재판\",\"content\":\"30일 대구지법에서 재판이 열렸다.\",\"summary\":\"열차사고 재판 요약\",\"writtenAt\":\"2026-01-30T03:21:55Z\"}}",
            "created_at": "2026-01-30T08:33:30.855654Z"
          },
          "source": {
            "table": "analysis_result_outbox",
            "connector": "postgresql"
          },
          "op": "c",
          "ts_ms": 1769762011022
        }
    """.trimIndent()

    @Test
    fun `deserializes Debezium envelope correctly`() {
        val envelope = objectMapper.readValue(cdcEventJson, DebeziumOutboxEnvelope::class.java)

        assertEquals("c", envelope.op)
        assertNull(envelope.before)
        assertNotNull(envelope.after)
        assertEquals(38L, envelope.after!!.id)
        assertEquals("2026-01-30-4903", envelope.after!!.articleId)
        assertEquals("analysis_result_outbox", envelope.source!!.table)
    }

    @Test
    fun `deserializes OutboxPayload with payload JSON string`() {
        val envelope = objectMapper.readValue(cdcEventJson, DebeziumOutboxEnvelope::class.java)
        val payload = envelope.after!!

        assertNotNull(payload.payload)
        assertTrue(payload.payload.contains("refinedArticle"))
        assertTrue(payload.payload.contains("incidentTypes"))
    }

    @Test
    fun `toAnalysisResult performs double deserialization`() {
        val envelope = objectMapper.readValue(cdcEventJson, DebeziumOutboxEnvelope::class.java)
        val analysisResult = envelope.after!!.toAnalysisResult(objectMapper)

        assertEquals("2026-01-30-4903", analysisResult.articleId)
        assertEquals("경부선 열차사고 재판", analysisResult.refinedArticle.title)
        assertEquals("30일 대구지법에서 재판이 열렸다.", analysisResult.refinedArticle.content)
        assertEquals(Instant.parse("2026-01-30T03:21:55Z"), analysisResult.refinedArticle.writtenAt)
        assertEquals(1, analysisResult.incidentTypes.size)
        assertEquals("TRAFFIC_ACCIDENT", analysisResult.incidentTypes.first().code)
        assertEquals("심각", analysisResult.urgency.name)
        assertEquals(7, analysisResult.urgency.level)
        assertEquals(1, analysisResult.keywords.size)
        assertEquals("열차사고", analysisResult.keywords[0].keyword)
        assertEquals(1, analysisResult.locations.size)
        assertEquals(35.647, analysisResult.locations[0].coordinate!!.lat)
        assertEquals("4782000000", analysisResult.locations[0].address.code)
    }

    @Test
    fun `ignores unknown fields in envelope`() {
        // ts_ms is an unknown field in our model, should be silently ignored
        val envelope = objectMapper.readValue(cdcEventJson, DebeziumOutboxEnvelope::class.java)

        assertNotNull(envelope)
        assertEquals("c", envelope.op)
    }
}
