package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.persistence.jpa.entity.article.UrgencyTypeEntity

object UrgencyMapper {

    fun toDomainModel(entity: UrgencyTypeEntity): Urgency =
        Urgency(
            name = entity.name,
            level = entity.level
        )

    fun toPersistenceModel(domain: Urgency): UrgencyTypeEntity =
        UrgencyTypeEntity(
            name = domain.name,
            level = domain.level
        )
}