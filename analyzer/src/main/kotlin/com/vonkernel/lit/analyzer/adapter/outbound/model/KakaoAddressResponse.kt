package com.vonkernel.lit.analyzer.adapter.outbound.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddressResponse(
    val meta: KakaoMeta,
    val documents: List<KakaoAddressDocument>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddressDocument(
    @param:JsonProperty("address_name")
    val addressName: String,

    @param:JsonProperty("address_type")
    val addressType: String,

    val x: String,
    val y: String,

    val address: KakaoAddress?,

    @param:JsonProperty("road_address")
    val roadAddress: KakaoRoadAddress?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddress(
    @param:JsonProperty("address_name")
    val addressName: String,

    @param:JsonProperty("region_1depth_name")
    val region1DepthName: String,

    @param:JsonProperty("region_2depth_name")
    val region2DepthName: String,

    @param:JsonProperty("region_3depth_name")
    val region3DepthName: String,

    @param:JsonProperty("region_3depth_h_name")
    val region3DepthHName: String,

    @param:JsonProperty("h_code")
    val hCode: String,

    @param:JsonProperty("b_code")
    val bCode: String,

    val x: String,
    val y: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoRoadAddress(
    @param:JsonProperty("address_name")
    val addressName: String,

    @param:JsonProperty("region_1depth_name")
    val region1DepthName: String,

    @param:JsonProperty("region_2depth_name")
    val region2DepthName: String,

    @param:JsonProperty("region_3depth_name")
    val region3DepthName: String,

    val x: String,
    val y: String
)
