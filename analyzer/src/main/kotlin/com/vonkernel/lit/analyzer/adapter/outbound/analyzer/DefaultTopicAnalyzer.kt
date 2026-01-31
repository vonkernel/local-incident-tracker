package com.vonkernel.lit.analyzer.adapter.outbound.analyzer

import com.vonkernel.lit.ai.application.PromptOrchestrator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.TopicExtractionInput
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.TopicExtractionOutput
import com.vonkernel.lit.analyzer.domain.port.analyzer.TopicAnalyzer
import com.vonkernel.lit.core.entity.Topic
import org.springframework.stereotype.Service

@Service
class DefaultTopicAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) : TopicAnalyzer {

    override suspend fun analyze(summary: String): Topic =
        promptOrchestrator.execute(
            promptId = "topic-extraction",
            input = TopicExtractionInput(summary = summary),
            inputType = TopicExtractionInput::class.java,
            outputType = TopicExtractionOutput::class.java
        ).result.let { output ->
            Topic(topic = output.topic)
        }
}
