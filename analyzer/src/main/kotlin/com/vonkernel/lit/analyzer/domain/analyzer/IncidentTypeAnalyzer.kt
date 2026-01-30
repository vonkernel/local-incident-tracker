package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.IncidentType

interface IncidentTypeAnalyzer {
    suspend fun analyze(title: String, content: String): Set<IncidentType>
}
