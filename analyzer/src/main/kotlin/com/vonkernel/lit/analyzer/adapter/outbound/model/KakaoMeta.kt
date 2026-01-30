package com.vonkernel.lit.analyzer.adapter.outbound.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoMeta(
    @param:JsonProperty("total_count")
    val totalCount: Int,

    @param:JsonProperty("pageable_count")
    val pageableCount: Int,

    @param:JsonProperty("is_end")
    val isEnd: Boolean
)
