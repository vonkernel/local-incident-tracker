package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.IncidentType

interface IncidentTypeAnalyzer {
    suspend fun analyze(article: Article): Set<IncidentType>
}
