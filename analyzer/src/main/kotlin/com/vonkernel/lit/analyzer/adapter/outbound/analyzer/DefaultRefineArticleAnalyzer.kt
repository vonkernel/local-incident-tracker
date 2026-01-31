package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.RefineArticleInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.RefineArticleOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.RefineArticleAnalyzer
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.RefinedArticle
import org.springframework.stereotype.Service

@Service
class DefaultRefineArticleAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : RefineArticleAnalyzer {

    override suspend fun analyze(article: Article): RefinedArticle =
        promptOrchestrator.execute(
            promptId = "refine-article",
            input = RefineArticleInput(title = article.title, content = article.content),
            inputType = RefineArticleInput::class.java,
            outputType = RefineArticleOutput::class.java
        ).result.let { output ->
            RefinedArticle(
                title = output.title,
                content = output.content,
                summary = output.summary,
                writtenAt = article.writtenAt
            )
        }
}
