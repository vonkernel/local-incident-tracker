package com.vonkernel.lit.analyzer.domain.model

data class KeywordExtractionOutput(
    val topic: String,
    val keywords: List<KeywordItem>
)

data class KeywordItem(
    val keyword: String,
    val priority: Int
)
