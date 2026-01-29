package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationInput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationOutput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeItem
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.repository.IncidentTypeRepository
import org.springframework.stereotype.Service

@Service
class DefaultIncidentTypeAnalyzer(
    private val promptOrchestrator: PromptOrchestrator,
    private val incidentTypeRepository: IncidentTypeRepository
) : IncidentTypeAnalyzer {

    override suspend fun analyze(article: Article): Set<IncidentType> {
        val incidentTypes = incidentTypeRepository.findAll()
        val incidentTypeItems = incidentTypes.map { IncidentTypeItem(code = it.code, name = it.name) }

        val input = IncidentTypeClassificationInput(
            title = article.title,
            content = article.content,
            incidentTypeList = IncidentTypeItem.formatList(incidentTypeItems)
        )

        val result = promptOrchestrator.execute(
            promptId = "incident-type-classification",
            input = input,
            inputType = IncidentTypeClassificationInput::class.java,
            outputType = IncidentTypeClassificationOutput::class.java
        )

        val validCodes = incidentTypes.associateBy { it.code }
        return result.result.incidentTypes
            .mapNotNull { item -> validCodes[item.code] }
            .toSet()
    }
}
