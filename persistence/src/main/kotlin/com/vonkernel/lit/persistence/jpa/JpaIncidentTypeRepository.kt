package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaIncidentTypeRepository : JpaRepository<IncidentTypeEntity, Long> {
    fun findByCodeIn(codes: Collection<String>): List<IncidentTypeEntity>
}