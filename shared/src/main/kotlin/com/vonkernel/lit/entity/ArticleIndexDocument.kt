package com.vonkernel.lit.entity

import java.time.ZonedDateTime

data class ArticleIndexDocument(
    // 문서 식별자
    val articleId: String,
    val sourceId: String,
    val originId: String,

    // 검색 필드 (Full-text indexed)
    val title: String,
    val content: String,
    val keywords: List<String>,
    val contentEmbedding: ByteArray,  // Semantic search를 위한 content 벡터 임베딩 [128]

    // 필터 및 집계 필드
    val incidentTypes: Set<IncidentType>,
    val urgency: Urgency,
    val incidentDate: ZonedDateTime,

    // 지리 정보
    val geoPoints: List<Coordinate>,
    val addresses: List<Address>, 
    val jurisdictionCodes: Set<String>,

    // 시간 정보 (순위, 필터링)
    val writtenAt: ZonedDateTime,
    val modifiedAt: ZonedDateTime
)