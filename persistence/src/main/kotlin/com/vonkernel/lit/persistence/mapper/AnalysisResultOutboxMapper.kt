package com.vonkernel.lit.persistence.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.entity.AnalysisResult
import com.vonkernel.lit.persistence.entity.outbox.AnalysisResultOutboxEntity

class AnalysisResultOutboxMapper(
    private val objectMapper: ObjectMapper
) {

    fun toPersistenceModel(analysisResult: AnalysisResult): AnalysisResultOutboxEntity {
        val payload = objectMapper.writeValueAsString(analysisResult)
        return AnalysisResultOutboxEntity(
            articleId = analysisResult.articleId,
            payload = payload
        )
    }
}
