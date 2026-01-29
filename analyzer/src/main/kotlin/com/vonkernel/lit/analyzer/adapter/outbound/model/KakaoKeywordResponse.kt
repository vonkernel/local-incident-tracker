package com.vonkernel.lit.analyzer.adapter.outbound.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoKeywordResponse(
    val meta: KakaoMeta,
    val documents: List<KakaoKeywordDocument>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoKeywordDocument(
    val id: String,

    @param:JsonProperty("place_name")
    val placeName: String,

    @param:JsonProperty("address_name")
    val addressName: String,

    @param:JsonProperty("road_address_name")
    val roadAddressName: String?,

    val x: String,
    val y: String
)
