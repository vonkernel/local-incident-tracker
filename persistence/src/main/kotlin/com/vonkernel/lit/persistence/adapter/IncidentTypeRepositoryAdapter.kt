package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.core.port.repository.IncidentTypeRepository
import com.vonkernel.lit.persistence.jpa.JpaIncidentTypeRepository
import com.vonkernel.lit.persistence.jpa.mapper.IncidentTypeMapper
import org.springframework.stereotype.Repository

@Repository
class IncidentTypeRepositoryAdapter(
    private val jpaRepository: JpaIncidentTypeRepository
) : IncidentTypeRepository {

    override fun findAll(): List<IncidentType> =
        jpaRepository.findAll()
            .map { IncidentTypeMapper.toDomainModel(it) }
}