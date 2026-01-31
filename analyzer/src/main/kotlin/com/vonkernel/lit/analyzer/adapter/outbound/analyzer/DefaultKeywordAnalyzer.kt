package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.KeywordExtractionInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.KeywordExtractionOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.KeywordAnalyzer
import com.vonkernel.lit.core.entity.Keyword
import org.springframework.stereotype.Service

@Service
class DefaultKeywordAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : KeywordAnalyzer {

    override suspend fun analyze(summary: String): List<Keyword> =
        promptOrchestrator.execute(
            promptId = "keyword-extraction",
            input = KeywordExtractionInput(summary = summary),
            inputType = KeywordExtractionInput::class.java,
            outputType = KeywordExtractionOutput::class.java
        ).result.keywords.map { Keyword(keyword = it.keyword, priority = it.priority) }
}
