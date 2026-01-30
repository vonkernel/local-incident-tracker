package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.repository.UrgencyRepository
import com.vonkernel.lit.persistence.jpa.JpaUrgencyTypeRepository
import com.vonkernel.lit.persistence.mapper.UrgencyMapper
import org.springframework.stereotype.Repository

@Repository
class UrgencyRepositoryAdapter(
    private val jpaRepository: JpaUrgencyTypeRepository
) : UrgencyRepository {

    override fun findAll(): List<Urgency> =
        jpaRepository.findAll()
            .map { UrgencyMapper.toDomainModel(it) }
}