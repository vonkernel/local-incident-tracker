package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.core.entity.IncidentType

interface IncidentTypeAnalyzer {
    suspend fun analyze(incidentTypes: List<IncidentType>, title: String, content: String): Set<IncidentType>
}
