package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.KeywordAnalysisResult
import com.vonkernel.lit.analyzer.domain.model.KeywordExtractionInput
import com.vonkernel.lit.analyzer.domain.model.KeywordExtractionOutput
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Keyword
import org.springframework.stereotype.Service

@Service
class DefaultKeywordAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : KeywordAnalyzer {

    override suspend fun analyze(article: Article): KeywordAnalysisResult {
        val input = KeywordExtractionInput(
            title = article.title,
            content = article.content
        )

        val result = promptOrchestrator.execute(
            promptId = "keyword-extraction",
            input = input,
            inputType = KeywordExtractionInput::class.java,
            outputType = KeywordExtractionOutput::class.java
        )

        return KeywordAnalysisResult(
            topic = result.result.topic,
            keywords = result.result.keywords.map { Keyword(keyword = it.keyword, priority = it.priority) }
        )
    }
}
