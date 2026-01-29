package com.vonkernel.lit.core.entity

/**
 * 사건 유형을 나타내는 데이터 클래스
 * 실제 사건 유형 목록은 데이터베이스에서 동적으로 관리된다.
 */
data class IncidentType(
    val code: String,    // 사건 유형 코드 (예: "forest_fire", "typhoon")
    val name: String     // 사건 유형 이름 (예: "산불", "태풍")
)