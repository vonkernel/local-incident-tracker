package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.jpa.entity.analysis.AddressEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaAddressRepository : JpaRepository<AddressEntity, Long> {
    fun findByRegionTypeAndCode(regionType: String, code: String): AddressEntity?
    fun findFirstByAddressName(addressName: String): AddressEntity?
}