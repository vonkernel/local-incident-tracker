package com.vonkernel.lit.indexer.adapter.outbound.embedding

import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider
import com.vonkernel.lit.ai.domain.port.EmbeddingExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@DisplayName("Indexer EmbeddingAdapter 테스트")
class EmbeddingAdapterTest {

    private val executor = mockk<EmbeddingExecutor>()
    private val adapter = EmbeddingAdapter(listOf(executor))

    init {
        every { executor.supports(LlmProvider.OPENAI) } returns true
    }

    @Test
    fun `embed 성공 시 FloatArray를 ByteArray로 변환하여 반환`() = runTest {
        // given
        val floats = floatArrayOf(1.0f, 2.0f, 3.0f)
        coEvery { executor.embed(any(), any(), any()) } returns floats

        // when
        val result = adapter.embed("테스트 텍스트")

        // then
        val expected = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { floats.forEach { putFloat(it) } }
            .array()
        assertThat(result).isEqualTo(expected)
        coVerify(exactly = 1) {
            executor.embed("테스트 텍스트", EmbeddingModel.TEXT_EMBEDDING_3_SMALL, 128)
        }
    }

    @Test
    fun `embedAll 성공 시 각 FloatArray를 ByteArray로 변환`() = runTest {
        // given
        val floats1 = floatArrayOf(1.0f, 2.0f)
        val floats2 = floatArrayOf(3.0f, 4.0f)
        coEvery { executor.embedAll(any(), any(), any()) } returns listOf(floats1, floats2)

        // when
        val result = adapter.embedAll(listOf("텍스트1", "텍스트2"))

        // then
        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo(toByteArray(floats1))
        assertThat(result[1]).isEqualTo(toByteArray(floats2))
        coVerify(exactly = 1) {
            executor.embedAll(listOf("텍스트1", "텍스트2"), EmbeddingModel.TEXT_EMBEDDING_3_SMALL, 128)
        }
    }

    @Test
    fun `embedAll에 빈 리스트 전달 시 빈 리스트 반환`() = runTest {
        // when
        val result = adapter.embedAll(emptyList())

        // then
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { executor.embedAll(any(), any(), any()) }
    }

    @Test
    fun `embedAll 실행 실패 시 null 리스트 반환`() = runTest {
        // given
        coEvery { executor.embedAll(any(), any(), any()) } throws RuntimeException("API error")

        // when
        val result = adapter.embedAll(listOf("텍스트1", "텍스트2", "텍스트3"))

        // then
        assertThat(result).hasSize(3)
        assertThat(result).containsExactly(null, null, null)
    }

    private fun toByteArray(floats: FloatArray): ByteArray =
        ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { floats.forEach { putFloat(it) } }
            .array()
}
