package com.vonkernel.lit.collector.adapter.outbound.news

import com.vonkernel.lit.collector.adapter.outbound.news.model.YonhapnewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("YonhapnewsArticleMapper")
class YonhapnewsArticleMapperTest {

    private val seoulZone = ZoneId.of("Asia/Seoul")

    private fun createYonhapnewsArticle(
        articleNo: Int = 12345,
        title: String = "테스트 제목",
        content: String = "테스트 본문 내용",
        publishedAt: String = "2024-01-15 10:30:00",
        writerName: String = "홍길동",
        createdAt: String = "2024/01/15 10:30:00.000000000",
    ) = YonhapnewsArticle(
        articleNo = articleNo,
        title = title,
        content = content,
        publishedAt = publishedAt,
        writerName = writerName,
        createdAt = createdAt,
    )

    @Test
    fun `기본 필드 매핑 검증`() {
        // Given
        val source = createYonhapnewsArticle(articleNo = 99999)

        // When
        val article = source.toArticle()

        // Then
        assertThat(article.sourceId).isEqualTo("yonhapnews")
        assertThat(article.originId).isEqualTo("99999")
        assertThat(article.sourceUrl).isNull()
    }

    @Test
    fun `articleId 생성 형식 검증`() {
        // Given
        val source = createYonhapnewsArticle(
            articleNo = 12345,
            publishedAt = "2024-01-15 10:30:00",
        )

        // When
        val article = source.toArticle()

        // Then
        assertThat(article.articleId).isEqualTo("2024-01-15-12345")
    }

    @Test
    fun `publishedAt 날짜 파싱 검증`() {
        // Given
        val source = createYonhapnewsArticle(publishedAt = "2024-01-15 10:30:00")

        // When
        val article = source.toArticle()

        // Then
        val expectedInstant = ZonedDateTime.of(
            LocalDateTime.of(2024, 1, 15, 10, 30, 0),
            seoulZone,
        ).toInstant()
        assertThat(article.writtenAt).isEqualTo(expectedInstant)
    }

    @Test
    fun `createdAt 날짜 파싱 검증`() {
        // Given
        val source = createYonhapnewsArticle(createdAt = "2024/01/15 10:30:00.000000000")

        // When
        val article = source.toArticle()

        // Then
        val expectedInstant = ZonedDateTime.of(
            LocalDateTime.of(2024, 1, 15, 10, 30, 0),
            seoulZone,
        ).toInstant()
        assertThat(article.modifiedAt).isEqualTo(expectedInstant)
    }

    @Test
    fun `title과 content 공백 제거 검증`() {
        // Given
        val source = createYonhapnewsArticle(
            title = "  앞뒤 공백이 있는 제목  ",
            content = "  앞뒤 공백이 있는 본문  ",
        )

        // When
        val article = source.toArticle()

        // Then
        assertThat(article.title).isEqualTo("앞뒤 공백이 있는 제목")
        assertThat(article.content).isEqualTo("앞뒤 공백이 있는 본문")
    }
}
