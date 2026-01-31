package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.domain.service.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationExtractionInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationExtractionOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationAnalyzer
import org.springframework.stereotype.Service

@Service
class DefaultLocationAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : LocationAnalyzer {

    override suspend fun analyze(title: String, content: String): List<ExtractedLocation> =
        promptOrchestrator.execute(
            promptId = "location-extraction",
            input = LocationExtractionInput(title = title, content = content),
            inputType = LocationExtractionInput::class.java,
            outputType = LocationExtractionOutput::class.java
        ).result.locations
            .distinctBy { it.name }
}
