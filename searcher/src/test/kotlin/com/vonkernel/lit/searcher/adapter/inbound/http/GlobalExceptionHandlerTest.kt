package com.vonkernel.lit.searcher.adapter.inbound.http

import com.vonkernel.lit.searcher.domain.exception.ArticleSearchException
import com.vonkernel.lit.searcher.domain.exception.InvalidSearchRequestException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `InvalidSearchRequestException 발생 시 400 Bad Request 반환`() {
        // given
        val exception = InvalidSearchRequestException("proximity 필터에는 latitude, longitude, distanceKm이 모두 필요합니다.")

        // when
        val response = handler.handleInvalidSearchRequest(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.get("error")).contains("proximity")
    }

    @Test
    fun `InvalidSearchRequestException 메시지가 null이면 기본 메시지 사용`() {
        // given
        val exception = mockk<InvalidSearchRequestException>()
        every { exception.message } returns null

        // when
        val response = handler.handleInvalidSearchRequest(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.get("error")).isEqualTo("Invalid request")
    }

    @Test
    fun `ArticleSearchException 발생 시 500 Internal Server Error 반환`() {
        // given
        val exception = ArticleSearchException("Search execution failed")

        // when
        val response = handler.handleArticleSearchException(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.get("error")).isEqualTo("Search execution failed")
    }

    @Test
    fun `일반 Exception 발생 시 500과 고정 메시지 반환`() {
        // given
        val exception = RuntimeException("unexpected error")

        // when
        val response = handler.handleUnexpectedException(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.get("error")).isEqualTo("Internal server error")
    }
}
