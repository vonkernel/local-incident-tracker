package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.entity.core.UrgencyTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaUrgencyTypeRepository : JpaRepository<UrgencyTypeEntity, Long> {
    fun findByName(name: String): UrgencyTypeEntity?
}