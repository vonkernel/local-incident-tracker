package com.vonkernel.lit.indexer.domain.port

interface Embedder {
    suspend fun embed(text: String): ByteArray
    suspend fun embedAll(texts: List<String>): List<ByteArray?>
}
