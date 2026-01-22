package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.AnalysisResult
import com.vonkernel.lit.persistence.entity.analysis.AnalysisResultEntity

object AnalysisResultMapper {

    fun toDomainModel(entity: AnalysisResultEntity): AnalysisResult =
        AnalysisResult(
            articleId = entity.article!!.articleId,
            incidentTypes = entity.incidentTypeMappings
                .mapNotNull { it.incidentType }
                .map { IncidentTypeMapper.toDomainModel(it) }
                .toSet(),
            urgency = entity.urgencyMapping!!.urgencyType!!
                .let { UrgencyMapper.toDomainModel(it) },
            keywords = entity.keywords
                .map { KeywordMapper.toDomainModel(it) },
            locations = entity.addressMappings
                .mapNotNull { it.address }
                .map { LocationMapper.toDomainModel(it) }
        )
}