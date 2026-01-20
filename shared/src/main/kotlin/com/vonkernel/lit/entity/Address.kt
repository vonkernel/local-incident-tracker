package com.vonkernel.lit.entity

data class Address(
    val regionType: RegionType,
    val code: String,
    val addressName: String,
    val depth1Name: String,
    val depth2Name: String? = null,
    val depth3Name: String? = null,
    val depth4Name: String? = null
)