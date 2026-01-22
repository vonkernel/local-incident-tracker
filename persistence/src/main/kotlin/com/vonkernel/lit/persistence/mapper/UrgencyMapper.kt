package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.Urgency
import com.vonkernel.lit.persistence.entity.core.UrgencyTypeEntity

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