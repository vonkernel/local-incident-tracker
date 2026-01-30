package com.vonkernel.lit.analyzer.adapter.inbound.model

import com.vonkernel.lit.analyzer.adapter.inbound.config.DebeziumObjectMapperConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("DebeziumArticleEvent 모델 변환 테스트")
class DebeziumArticleEventTest {

    private val objectMapper = DebeziumObjectMapperConfig().debeziumObjectMapper()

    @Test
    @DisplayName("ArticlePayload.toArticle() - 모든 필드가 정상 매핑된다")
    fun toArticle_allFieldsMapped() {
        // Given
        val payload = ArticlePayload(
            articleId = "article-001",
            originId = "origin-001",
            sourceId = "yonhapnews",
            writtenAt = "2025-01-15T09:30:00Z",
            modifiedAt = "2025-01-15T10:00:00Z",
            title = "테스트 기사 제목",
            content = "테스트 기사 본문 내용",
            sourceUrl = "https://example.com/article/001"
        )

        // When
        val article = payload.toArticle()

        // Then
        assertThat(article.articleId).isEqualTo("article-001")
        assertThat(article.originId).isEqualTo("origin-001")
        assertThat(article.sourceId).isEqualTo("yonhapnews")
        assertThat(article.writtenAt).isEqualTo(Instant.parse("2025-01-15T09:30:00Z"))
        assertThat(article.modifiedAt).isEqualTo(Instant.parse("2025-01-15T10:00:00Z"))
        assertThat(article.title).isEqualTo("테스트 기사 제목")
        assertThat(article.content).isEqualTo("테스트 기사 본문 내용")
        assertThat(article.sourceUrl).isEqualTo("https://example.com/article/001")
    }

    @Test
    @DisplayName("ArticlePayload.toArticle() - sourceUrl이 null인 경우 null로 매핑된다")
    fun toArticle_nullSourceUrl() {
        // Given
        val payload = ArticlePayload(
            articleId = "article-002",
            originId = "origin-002",
            sourceId = "yonhapnews",
            writtenAt = "2025-01-15T09:30:00Z",
            modifiedAt = "2025-01-15T10:00:00Z",
            title = "제목",
            content = "본문",
            sourceUrl = null
        )

        // When
        val article = payload.toArticle()

        // Then
        assertThat(article.sourceUrl).isNull()
    }

    @Test
    @DisplayName("ArticlePayload.toArticle() - ISO instant 형식의 writtenAt/modifiedAt이 올바르게 파싱된다")
    fun toArticle_instantParsing() {
        // Given
        val payload = ArticlePayload(
            articleId = "article-003",
            originId = "origin-003",
            sourceId = "yonhapnews",
            writtenAt = "2024-12-31T23:59:59.999Z",
            modifiedAt = "2025-01-01T00:00:00.001Z",
            title = "제목",
            content = "본문"
        )

        // When
        val article = payload.toArticle()

        // Then
        assertThat(article.writtenAt).isEqualTo(Instant.parse("2024-12-31T23:59:59.999Z"))
        assertThat(article.modifiedAt).isEqualTo(Instant.parse("2025-01-01T00:00:00.001Z"))
        assertThat(article.writtenAt).isBefore(article.modifiedAt)
    }

    @Test
    @DisplayName("실제 Debezium CDC JSON 메시지가 DebeziumEnvelope로 역직렬화되고 Article로 변환된다")
    fun realDebeziumJson_deserializesToEnvelopeAndArticle() {
        // Given
        val json = """
        {
            "before": null,
            "after": {
                "article_id": "2026-01-29-4846",
                "origin_id": "4846",
                "source_id": "yonhapnews",
                "written_at": "2026-01-29T05:53:12.000000Z",
                "modified_at": "2026-01-29T05:54:01.000000Z",
                "title": "대구시, 탄력적 입산 통제로 산불 위험 줄이고 주민 불편 최소화",
                "content": "대구시, 탄력적 입산 통제로 산불 위험 줄이고 주민 불편 최소화\n\n    (대구=연합뉴스) 이강일 기자 = 대구시는 산불 발생 위험을 낮추고 시민 불편을 최소화하기 위해 '탄력적 입산 통제'를 시행한다고 29일 밝혔다.",
                "source_url": null,
                "created_at": "2026-01-29T05:55:26.886027Z",
                "updated_at": "2026-01-29T05:55:26.886028Z"
            },
            "source": {
                "version": "3.4.1.Final",
                "connector": "postgresql",
                "name": "lit",
                "ts_ms": 1769666126893,
                "snapshot": "false",
                "db": "lit_maindb",
                "sequence": "[\"31717400\",\"31720280\"]",
                "ts_us": 1769666126893509,
                "ts_ns": 1769666126893509000,
                "schema": "public",
                "table": "article",
                "txId": 833,
                "lsn": 31720280,
                "xmin": null
            },
            "transaction": null,
            "op": "c",
            "ts_ms": 1769666127303,
            "ts_us": 1769666127303176,
            "ts_ns": 1769666127303176096
        }
        """.trimIndent()

        // When: JSON → DebeziumEnvelope
        val envelope = objectMapper.readValue(json, DebeziumEnvelope::class.java)

        // Then: DebeziumEnvelope 필드 검증
        assertThat(envelope.op).isEqualTo("c")
        assertThat(envelope.before).isNull()
        assertThat(envelope.after).isNotNull
        assertThat(envelope.source).isNotNull
        assertThat(envelope.source!!.table).isEqualTo("article")
        assertThat(envelope.source!!.connector).isEqualTo("postgresql")

        // When: ArticlePayload → Article
        val article = envelope.after!!.toArticle()

        // Then: Article 필드 검증
        assertThat(article.articleId).isEqualTo("2026-01-29-4846")
        assertThat(article.originId).isEqualTo("4846")
        assertThat(article.sourceId).isEqualTo("yonhapnews")
        assertThat(article.writtenAt).isEqualTo(Instant.parse("2026-01-29T05:53:12.000000Z"))
        assertThat(article.modifiedAt).isEqualTo(Instant.parse("2026-01-29T05:54:01.000000Z"))
        assertThat(article.title).isEqualTo("대구시, 탄력적 입산 통제로 산불 위험 줄이고 주민 불편 최소화")
        assertThat(article.content).contains("대구시는 산불 발생 위험을 낮추고")
        assertThat(article.sourceUrl).isNull()
    }
}
