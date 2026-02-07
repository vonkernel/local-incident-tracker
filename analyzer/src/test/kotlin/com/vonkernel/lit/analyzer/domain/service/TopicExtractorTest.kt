package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.TopicAnalyzer
import com.vonkernel.lit.core.entity.Topic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TopicExtractor 테스트")
class TopicExtractorTest {

    private val topicAnalyzer: TopicAnalyzer = mockk()

    private lateinit var service: TopicExtractor

    private val articleId = "article-001"
    private val summary = "서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다."

    private val expectedTopic = Topic("서울 강남구에서 대형 화재가 발생하여 소방당국이 출동했다")

    @BeforeEach
    fun setUp() {
        service = TopicExtractor(topicAnalyzer)
    }

    @Test
    @DisplayName("토픽 추출 성공")
    fun `토픽 추출 성공`() = runTest {
        // Given
        coEvery { topicAnalyzer.analyze(summary) } returns expectedTopic

        // When
        val result = service.process(articleId, summary)

        // Then
        assertThat(result).isEqualTo(expectedTopic)
        coVerify(exactly = 1) { topicAnalyzer.analyze(summary) }
    }

    @Test
    @DisplayName("분석 실패 시 재시도 후 성공")
    fun `분석 실패 시 재시도 후 성공`() = runTest {
        // Given
        coEvery { topicAnalyzer.analyze(summary) } throws
            RuntimeException("일시적 오류") andThen expectedTopic

        // When
        val result = service.process(articleId, summary)

        // Then
        assertThat(result).isEqualTo(expectedTopic)
        coVerify(exactly = 2) { topicAnalyzer.analyze(summary) }
    }
}
