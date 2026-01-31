package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.persistence.jpa.entity.article.IncidentTypeEntity

object IncidentTypeMapper {

    fun toDomainModel(entity: IncidentTypeEntity): IncidentType =
        IncidentType(
            code = entity.code,
            name = entity.name
        )

    fun toPersistenceModel(domain: IncidentType): IncidentTypeEntity =
        IncidentTypeEntity(
            code = domain.code,
            name = domain.name
        )
}