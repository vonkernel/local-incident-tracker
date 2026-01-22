package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.IncidentType
import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity

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