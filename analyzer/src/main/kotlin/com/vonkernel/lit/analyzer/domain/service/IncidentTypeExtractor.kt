package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.port.repository.IncidentTypeRepository
import org.springframework.stereotype.Service

@Service
class IncidentTypeExtractor(
    private val incidentTypeRepository: IncidentTypeRepository,
    private val incidentTypeAnalyzer: IncidentTypeAnalyzer
) : RetryableAnalysisService() {

    suspend fun process(articleId: String, title: String, content: String): Set<IncidentType> =
        withRetry("extract-incident-type", articleId) {
            incidentTypeRepository.findAll().let { incidentTypes ->
                incidentTypeAnalyzer.analyze(incidentTypes, title, content)
            }
        }
}