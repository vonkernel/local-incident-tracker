package com.vonkernel.lit.core.entity

data class Address(
    val regionType: RegionType,
    val code: String,              // 지역 코드 (Location API로부터 획득, 식별 불가시 "UNKNOWN")
    val addressName: String,       // 전체 주소명
    val depth1Name: String? = null,  // 시/도 (깊이 1)
    val depth2Name: String? = null,  // 시/군/구 (깊이 2)
    val depth3Name: String? = null,  // 읍/면/동 (깊이 3)
)