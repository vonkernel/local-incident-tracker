package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.Topic
import com.vonkernel.lit.persistence.jpa.entity.analysis.TopicAnalysisEntity

object TopicMapper {

    fun toDomainModel(entity: TopicAnalysisEntity): Topic =
        Topic(topic = entity.topic)

    fun toPersistenceModel(domain: Topic): TopicAnalysisEntity =
        TopicAnalysisEntity(topic = domain.topic)
}
