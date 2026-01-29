package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.persistence.entity.analysis.AddressEntity
import com.vonkernel.lit.persistence.entity.analysis.AddressMappingEntity
import com.vonkernel.lit.persistence.entity.analysis.AnalysisResultEntity
import com.vonkernel.lit.persistence.entity.analysis.IncidentTypeMappingEntity
import com.vonkernel.lit.persistence.entity.analysis.UrgencyMappingEntity
import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity
import com.vonkernel.lit.persistence.entity.core.UrgencyTypeEntity
import com.vonkernel.lit.persistence.jpa.JpaAddressRepository
import com.vonkernel.lit.persistence.jpa.JpaAnalysisResultOutboxRepository
import com.vonkernel.lit.persistence.jpa.JpaAnalysisResultRepository
import com.vonkernel.lit.persistence.jpa.JpaIncidentTypeRepository
import com.vonkernel.lit.persistence.jpa.JpaUrgencyTypeRepository
import com.vonkernel.lit.persistence.mapper.AnalysisResultMapper
import com.vonkernel.lit.persistence.mapper.AnalysisResultOutboxMapper
import com.vonkernel.lit.persistence.mapper.KeywordMapper
import com.vonkernel.lit.persistence.mapper.LocationMapper
import com.vonkernel.lit.core.repository.AnalysisResultRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class AnalysisResultRepositoryAdapter(
    private val jpaAnalysisResultRepository: JpaAnalysisResultRepository,
    private val jpaAnalysisResultOutboxRepository: JpaAnalysisResultOutboxRepository,
    private val jpaIncidentTypeRepository: JpaIncidentTypeRepository,
    private val jpaUrgencyTypeRepository: JpaUrgencyTypeRepository,
    private val jpaAddressRepository: JpaAddressRepository,
    private val outboxMapper: AnalysisResultOutboxMapper
) : AnalysisResultRepository {

    @Transactional
    override fun save(analysisResult: AnalysisResult): AnalysisResult =
        buildAnalysisResultEntity(analysisResult)
            .let { jpaAnalysisResultRepository.save(it) }
            .also { jpaAnalysisResultOutboxRepository.save(outboxMapper.toPersistenceModel(analysisResult)) }
            .let { AnalysisResultMapper.toDomainModel(it) }

    private fun buildAnalysisResultEntity(analysisResult: AnalysisResult): AnalysisResultEntity =
        AnalysisResultEntity(articleId = analysisResult.articleId).apply {
            createUrgencyMapping(loadUrgency(analysisResult)).setupAnalysisResult(this)
            createIncidentTypeMappings(loadIncidentTypes(analysisResult)).forEach { it.setupAnalysisResult(this) }
            createAddressMappings(loadOrCreateAddresses(analysisResult)).forEach { it.setupAnalysisResult(this) }
            createKeywords(analysisResult).forEach { it.setupAnalysisResult(this) }
        }

    private fun loadIncidentTypes(analysisResult: AnalysisResult): List<IncidentTypeEntity> =
        jpaIncidentTypeRepository.findByCodeIn(analysisResult.incidentTypes.map { it.code })

    private fun loadUrgency(analysisResult: AnalysisResult): UrgencyTypeEntity =
        jpaUrgencyTypeRepository.findByName(analysisResult.urgency.name)
            ?: throw IllegalArgumentException("Urgency not found: ${analysisResult.urgency.name}")

    private fun loadOrCreateAddresses(analysisResult: AnalysisResult) =
        analysisResult.locations.map { location ->
            val regionCode = location.address.regionType.code
            val addressCode = location.address.code
            jpaAddressRepository.findByRegionTypeAndCode(regionCode, addressCode)
                ?: jpaAddressRepository.save(LocationMapper.toPersistenceModel(location))
        }

    private fun createUrgencyMapping(urgencyTypeEntity: UrgencyTypeEntity) =
        UrgencyMappingEntity(urgencyType = urgencyTypeEntity)

    private fun createIncidentTypeMappings(incidentTypeEntities: List<IncidentTypeEntity>) =
        incidentTypeEntities.map { IncidentTypeMappingEntity(incidentType = it) }

    private fun createAddressMappings(addressEntities: List<AddressEntity>) =
        addressEntities.map { AddressMappingEntity(address = it) }

    private fun createKeywords(analysisResult: AnalysisResult) =
        analysisResult.keywords.map { KeywordMapper.toPersistenceModel(it) }
}