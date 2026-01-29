package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.LocationExtractionInput
import com.vonkernel.lit.analyzer.domain.model.LocationExtractionOutput
import com.vonkernel.lit.core.entity.Article
import org.springframework.stereotype.Service

@Service
class DefaultLocationAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : LocationAnalyzer {

    override suspend fun analyze(article: Article): List<ExtractedLocation> {
        val input = LocationExtractionInput(
            title = article.title,
            content = article.content
        )

        val result = promptOrchestrator.execute(
            promptId = "location-extraction",
            input = input,
            inputType = LocationExtractionInput::class.java,
            outputType = LocationExtractionOutput::class.java
        )

        return result.result.locations
    }
}
