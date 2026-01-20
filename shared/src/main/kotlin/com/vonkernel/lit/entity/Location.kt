package com.vonkernel.lit.entity

data class Location(
    val coordinate: Coordinate,
    val addresses: List<Address>
)