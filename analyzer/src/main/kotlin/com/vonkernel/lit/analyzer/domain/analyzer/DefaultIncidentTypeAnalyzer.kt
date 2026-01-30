package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationInput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeClassificationOutput
import com.vonkernel.lit.analyzer.domain.model.IncidentTypeItem
import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.repository.IncidentTypeRepository
import org.springframework.stereotype.Service

@Service
class DefaultIncidentTypeAnalyzer(
    private val promptOrchestrator: PromptOrchestrator,
    private val incidentTypeRepository: IncidentTypeRepository
) : IncidentTypeAnalyzer {

    override suspend fun analyze(title: String, content: String): Set<IncidentType> =
        incidentTypeRepository.findAll().let { incidentTypes ->
            promptOrchestrator.execute(
                promptId = "incident-type-classification",
                input = IncidentTypeClassificationInput(
                    title = title,
                    content = content,
                    incidentTypeList = IncidentTypeItem.formatList(
                        incidentTypes.map { IncidentTypeItem(code = it.code, name = it.name) }
                    )
                ),
                inputType = IncidentTypeClassificationInput::class.java,
                outputType = IncidentTypeClassificationOutput::class.java
            ).result.incidentTypes
                .mapNotNull { item -> incidentTypes.associateBy { it.code }[item.code] }
                .toSet()
        }
}
