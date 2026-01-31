package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyAssessmentInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyAssessmentOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.UrgencyItem
import com.vonkernel.lit.analyzer.domain.port.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.repository.UrgencyRepository
import org.springframework.stereotype.Service

@Service
class DefaultUrgencyAnalyzer(
    private val promptOrchestrator: PromptOrchestrator,
    private val urgencyRepository: UrgencyRepository
) : UrgencyAnalyzer {

    override suspend fun analyze(title: String, content: String): Urgency =
        urgencyRepository.findAll().let { urgencies ->
            promptOrchestrator.execute(
                promptId = "urgency-assessment",
                input = UrgencyAssessmentInput(
                    title = title,
                    content = content,
                    urgencyTypeList = UrgencyItem.formatList(
                        urgencies.map { UrgencyItem(name = it.name, level = it.level) }
                    )
                ),
                inputType = UrgencyAssessmentInput::class.java,
                outputType = UrgencyAssessmentOutput::class.java
            ).result.urgency.let { assessed ->
                urgencies.associateBy { it.name }[assessed.name]
                    ?: Urgency(name = assessed.name, level = assessed.level)
            }
        }
}
