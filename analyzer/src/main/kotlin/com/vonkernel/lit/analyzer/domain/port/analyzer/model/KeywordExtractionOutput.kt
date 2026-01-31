package com.vonkernel.lit.analyzer.domain.port.analyzer.model

data class KeywordExtractionOutput(
    val keywords: List<KeywordItem>
)

data class KeywordItem(
    val keyword: String,
    val priority: Int
)
