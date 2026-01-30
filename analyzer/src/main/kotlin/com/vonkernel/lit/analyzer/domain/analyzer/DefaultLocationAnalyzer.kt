package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.LocationExtractionInput
import com.vonkernel.lit.analyzer.domain.model.LocationExtractionOutput
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
        ).result.location.let(::listOf)
}
