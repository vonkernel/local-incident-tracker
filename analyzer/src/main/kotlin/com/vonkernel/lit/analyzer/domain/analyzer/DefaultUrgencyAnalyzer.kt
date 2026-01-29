package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.UrgencyAssessmentInput
import com.vonkernel.lit.analyzer.domain.model.UrgencyAssessmentOutput
import com.vonkernel.lit.analyzer.domain.model.UrgencyItem
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.repository.UrgencyRepository
import org.springframework.stereotype.Service

@Service
class DefaultUrgencyAnalyzer(
    private val promptOrchestrator: PromptOrchestrator,
    private val urgencyRepository: UrgencyRepository
) : UrgencyAnalyzer {

    override suspend fun analyze(article: Article): Urgency {
        val urgencies = urgencyRepository.findAll()
        val urgencyItems = urgencies.map { UrgencyItem(name = it.name, level = it.level) }

        val input = UrgencyAssessmentInput(
            title = article.title,
            content = article.content,
            urgencyTypeList = UrgencyItem.formatList(urgencyItems)
        )

        val result = promptOrchestrator.execute(
            promptId = "urgency-assessment",
            input = input,
            inputType = UrgencyAssessmentInput::class.java,
            outputType = UrgencyAssessmentOutput::class.java
        )

        val validUrgencies = urgencies.associateBy { it.name }
        return validUrgencies[result.result.urgency.name]
            ?: Urgency(name = result.result.urgency.name, level = result.result.urgency.level)
    }
}
