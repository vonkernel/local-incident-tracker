package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.ArticleValidationException
import com.vonkernel.lit.core.entity.Article
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ArticleValidator")
class ArticleValidatorTest {

    private val testTime = Instant.parse("2024-01-15T10:00:00Z")

    private fun createArticle(
        articleId: String = "test-article-1",
        originId: String = "origin-1",
        sourceId: String = "source-1",
        title: String = "테스트 제목",
        content: String = "테스트 본문 내용",
    ) = Article(
        articleId = articleId,
        originId = originId,
        sourceId = sourceId,
        writtenAt = testTime,
        modifiedAt = testTime,
        title = title,
        content = content,
    )

    @Test
    fun `유효한 Article은 성공 결과 반환`() {
        // Given
        val article = createArticle()

        // When
        val result = article.validate()

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(article)
    }

    @Test
    fun `빈 title은 검증 실패`() {
        // Given
        val article = createArticle(title = "")

        // When
        val result = article.validate()

        // Then
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as ArticleValidationException
        assertThat(exception.errors).containsExactly("title cannot be blank")
        assertThat(exception.articleId).isEqualTo(article.articleId)
    }

    @Test
    fun `빈 content는 검증 실패`() {
        // Given
        val article = createArticle(content = "")

        // When
        val result = article.validate()

        // Then
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as ArticleValidationException
        assertThat(exception.errors).containsExactly("content cannot be blank")
        assertThat(exception.articleId).isEqualTo(article.articleId)
    }

    @Test
    fun `빈 originId는 검증 실패`() {
        // Given
        val article = createArticle(originId = "")

        // When
        val result = article.validate()

        // Then
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as ArticleValidationException
        assertThat(exception.errors).containsExactly("originId cannot be blank")
        assertThat(exception.articleId).isEqualTo(article.articleId)
    }

    @Test
    fun `빈 sourceId는 검증 실패`() {
        // Given
        val article = createArticle(sourceId = "")

        // When
        val result = article.validate()

        // Then
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as ArticleValidationException
        assertThat(exception.errors).containsExactly("sourceId cannot be blank")
        assertThat(exception.articleId).isEqualTo(article.articleId)
    }

    @Test
    fun `여러 필드가 비어있으면 모든 오류 포함`() {
        // Given
        val article = createArticle(
            title = "",
            content = "",
            originId = "",
            sourceId = "",
        )

        // When
        val result = article.validate()

        // Then
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as ArticleValidationException
        assertThat(exception.errors).containsExactly(
            "title cannot be blank",
            "content cannot be blank",
            "originId cannot be blank",
            "sourceId cannot be blank",
        )
        assertThat(exception.articleId).isEqualTo(article.articleId)
    }
}
