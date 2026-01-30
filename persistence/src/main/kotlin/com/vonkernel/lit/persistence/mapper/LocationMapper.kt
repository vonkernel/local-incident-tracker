package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.persistence.entity.analysis.AddressEntity

object LocationMapper {

    fun toDomainModel(entity: AddressEntity): Location =
        Location(
            coordinate = if (entity.coordinate != null) CoordinateMapper.toDomainModel(entity.coordinate!!) else null,
            address = entity.run {
                Address(
                    regionType = RegionType.entries.find { it.code == regionType } ?: RegionType.UNKNOWN,
                    code = code,
                    addressName = addressName,
                    depth1Name = depth1Name,
                    depth2Name = depth2Name,
                    depth3Name = depth3Name
                )
            }
        )

    fun toPersistenceModel(domain: Location): AddressEntity =
        domain.coordinate?.let { CoordinateMapper.toPersistenceModel(it) }
            .let { coordinateEntity ->
                AddressEntity(
                    regionType = domain.address.regionType.code,
                    code = domain.address.code,
                    addressName = domain.address.addressName,
                    depth1Name = domain.address.depth1Name,
                    depth2Name = domain.address.depth2Name,
                    depth3Name = domain.address.depth3Name
                ).apply {
                    this.coordinate = coordinateEntity
                    coordinateEntity?.address = this
                }
            }
}