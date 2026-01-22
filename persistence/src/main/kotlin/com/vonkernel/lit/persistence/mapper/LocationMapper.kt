package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.Address
import com.vonkernel.lit.entity.Location
import com.vonkernel.lit.entity.RegionType
import com.vonkernel.lit.persistence.entity.analysis.AddressEntity

object LocationMapper {

    fun toDomainModel(entity: AddressEntity): Location =
        Location(
            coordinate = CoordinateMapper.toDomainModel(entity.coordinate!!),
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
        CoordinateMapper.toPersistenceModel(domain.coordinate)
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
                    coordinateEntity.address = this
                }
            }
}