package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.domain.service.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationExtractionOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationValidationInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationValidator
import org.springframework.stereotype.Service

@Service
class DefaultLocationValidator(
    private val promptOrchestrator: PromptOrchestrator
) : LocationValidator {

    override suspend fun validate(
        title: String,
        content: String,
        candidates: List<ExtractedLocation>
    ): List<ExtractedLocation> {
        if (candidates.isEmpty()) return emptyList()

        return promptOrchestrator.execute(
            promptId = "location-validation",
            input = LocationValidationInput(
                title = title,
                content = content,
                extractedLocations = candidates.joinToString("\n") { "- ${it.name} (${it.type})" }
            ),
            inputType = LocationValidationInput::class.java,
            outputType = LocationExtractionOutput::class.java
        ).result.locations
    }
}
