package com.vonkernel.lit.indexer.adapter.outbound.embedding

import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.port.EmbeddingExecutor
import com.vonkernel.lit.indexer.domain.port.Embedder
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Component
class EmbeddingAdapter(
    private val embeddingExecutors: List<EmbeddingExecutor>
) : Embedder {

    companion object {
        private val MODEL = EmbeddingModel.TEXT_EMBEDDING_3_SMALL
        private const val DIMENSIONS = 128
    }

    override suspend fun embed(text: String): ByteArray =
        findExecutor()
            .embed(text, MODEL, DIMENSIONS)
            .toByteArray()

    override suspend fun embedAll(texts: List<String>): List<ByteArray?> =
        texts.takeIf { it.isNotEmpty() }
            ?.let { runCatching { findExecutor().embedAll(it, MODEL, DIMENSIONS).map { floats -> floats.toByteArray() } } }
            ?.getOrElse { List(texts.size) { null } }
            ?: emptyList()

    private fun findExecutor() = embeddingExecutors.first { it.supports(MODEL.provider) }

    private fun FloatArray.toByteArray(): ByteArray =
        ByteBuffer.allocate(size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { forEach { putFloat(it) } }
            .array()
}
