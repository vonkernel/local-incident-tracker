package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Urgency

interface UrgencyAnalyzer {
    suspend fun analyze(article: Article): Urgency
}
