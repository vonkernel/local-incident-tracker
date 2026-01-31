package com.vonkernel.lit.indexer.adapter.outbound.embedding

import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider
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

    override suspend fun embed(text: String): ByteArray {
        val executor = embeddingExecutors.first { it.supports(MODEL.provider) }
        val floats = executor.embed(text, MODEL, DIMENSIONS)
        return floatsToByteArray(floats)
    }

    override suspend fun embedAll(texts: List<String>): List<ByteArray?> {
        if (texts.isEmpty()) return emptyList()

        return try {
            val executor = embeddingExecutors.first { it.supports(MODEL.provider) }
            val floatsList = executor.embedAll(texts, MODEL, DIMENSIONS)
            floatsList.map { floatsToByteArray(it) }
        } catch (e: Exception) {
            List(texts.size) { null }
        }
    }

    private fun floatsToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.BIG_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
