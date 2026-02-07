package com.vonkernel.lit.searcher.adapter.outbound.embedding

import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider
import com.vonkernel.lit.ai.domain.port.EmbeddingExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@DisplayName("Searcher EmbeddingAdapter 테스트")
class EmbeddingAdapterTest {

    private val executor = mockk<EmbeddingExecutor>()
    private val adapter = EmbeddingAdapter(listOf(executor))

    init {
        every { executor.supports(LlmProvider.OPENAI) } returns true
    }

    @Test
    fun `embed 성공 시 FloatArray를 ByteArray로 변환하여 반환`() = runTest {
        // given
        val floats = floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f)
        coEvery { executor.embed(any(), any(), any()) } returns floats

        // when
        val result = adapter.embed("서울 폭우 피해")

        // then
        val expected = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { floats.forEach { putFloat(it) } }
            .array()
        assertThat(result).isEqualTo(expected)
        coVerify(exactly = 1) {
            executor.embed("서울 폭우 피해", EmbeddingModel.TEXT_EMBEDDING_3_SMALL, 128)
        }
    }

    @Test
    fun `지원하는 executor가 없으면 NoSuchElementException 발생`() = runTest {
        // given
        val unsupportedExecutor = mockk<EmbeddingExecutor>()
        every { unsupportedExecutor.supports(any()) } returns false
        val adapterWithNoSupport = EmbeddingAdapter(listOf(unsupportedExecutor))

        // when & then
        assertThatThrownBy { kotlinx.coroutines.runBlocking { adapterWithNoSupport.embed("테스트") } }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}
