package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.jpa.entity.article.UrgencyTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaUrgencyTypeRepository : JpaRepository<UrgencyTypeEntity, Long> {
    fun findByName(name: String): UrgencyTypeEntity?
}