package com.vonkernel.lit.analyzer.adapter.inbound.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("DebeziumArticleEvent 모델 변환 테스트")
class DebeziumArticleEventTest {

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
}
