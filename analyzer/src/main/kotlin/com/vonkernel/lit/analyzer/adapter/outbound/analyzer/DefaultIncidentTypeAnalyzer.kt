package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.domain.service.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.IncidentTypeClassificationInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.IncidentTypeClassificationOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.IncidentTypeItem
import com.vonkernel.lit.core.entity.IncidentType
import org.springframework.stereotype.Service

@Service
class DefaultIncidentTypeAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : IncidentTypeAnalyzer {

    override suspend fun analyze(incidentTypes: List<IncidentType>, title: String, content: String): Set<IncidentType> =
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
