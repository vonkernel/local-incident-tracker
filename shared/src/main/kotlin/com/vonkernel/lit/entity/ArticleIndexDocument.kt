package com.vonkernel.lit.entity

import java.time.ZonedDateTime

data class ArticleIndexDocument(
    // 문서 식별자 (필수)
    val articleId: String,

    // 문서 메타데이터 (선택)
    val sourceId: String? = null,
    val originId: String? = null,

    // 검색 필드 (Full-text indexed, 선택)
    val title: String? = null,
    val content: String? = null,
    val keywords: List<String>? = null,
    val contentEmbedding: ByteArray? = null,  // Semantic search를 위한 content 벡터 임베딩 [128]

    // 필터 및 집계 필드 (선택)
    val incidentTypes: Set<IncidentType>? = null,
    val urgency: Urgency? = null,
    val incidentDate: ZonedDateTime? = null,

    // 지리 정보 (선택)
    val geoPoints: List<Coordinate>? = null,
    val addresses: List<Address>? = null,
    val jurisdictionCodes: Set<String>? = null,

    // 시간 정보 (순위, 필터링, 선택)
    val writtenAt: ZonedDateTime? = null,
    val modifiedAt: ZonedDateTime? = null
)