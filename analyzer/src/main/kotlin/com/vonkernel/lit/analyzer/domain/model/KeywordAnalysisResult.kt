package com.vonkernel.lit.analyzer.domain.model

import com.vonkernel.lit.core.entity.Keyword

data class KeywordAnalysisResult(
    val topic: String,
    val keywords: List<Keyword>
)
