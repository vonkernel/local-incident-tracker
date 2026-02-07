package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.port.repository.AddressCacheRepository
import com.vonkernel.lit.persistence.jpa.JpaAddressRepository
import com.vonkernel.lit.persistence.jpa.mapper.LocationMapper
import org.springframework.stereotype.Repository

@Repository
class AddressCacheRepositoryAdapter(
    private val jpaRepository: JpaAddressRepository
) : AddressCacheRepository {

    override fun findByAddressName(addressName: String): Location? =
        jpaRepository.findFirstByAddressName(addressName)
            ?.let(LocationMapper::toDomainModel)
}
