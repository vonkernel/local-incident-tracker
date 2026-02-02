package com.vonkernel.lit.searcher.domain.port

interface Embedder {
    suspend fun embed(text: String): ByteArray
}
