package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.persistence.jpa.entity.analysis.AddressCoordinateEntity

object CoordinateMapper {

    fun toDomainModel(entity: AddressCoordinateEntity): Coordinate =
        Coordinate(
            lat = entity.latitude,
            lon = entity.longitude
        )

    fun toPersistenceModel(domain: Coordinate): AddressCoordinateEntity =
        AddressCoordinateEntity(
            latitude = domain.lat,
            longitude = domain.lon
        )
}