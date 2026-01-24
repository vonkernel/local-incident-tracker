package com.vonkernel.lit.collector.adapter.outbound

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Article structure from Yonhapnews safety data API.
 *
 * Uses Jackson @JsonProperty to map API field names to Kotlin naming conventions.
 */
data class YonhapnewsArticle(
    @field:JsonProperty("YNA_NO")
    val articleNo: Int,

    @field:JsonProperty("YNA_TTL")
    val title: String,

    @field:JsonProperty("YNA_CN")
    val content: String,

    @field:JsonProperty("YNA_YMD")
    val publishedAt: String,        // "yyyy-MM-dd HH:mm:ss"

    @field:JsonProperty("YNA_WRTR_NM")
    val writerName: String,

    @field:JsonProperty("CRT_DT")
    val createdAt: String            // "yyyy/MM/dd HH:mm:ss.SSSSSSSSS"
)
