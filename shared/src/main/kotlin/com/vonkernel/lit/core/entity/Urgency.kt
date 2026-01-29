package com.vonkernel.lit.core.entity

/**
 * 긴급도/중요도를 나타내는 데이터 클래스
 * 실제 긴급도 목록은 데이터베이스에서 동적으로 관리된다.
 */
data class Urgency(
    val name: String,    // 긴급도 이름 (예: "긴급", "중요", "정보")
    val level: Int       // 긴급도 레벨 (숫자가 클수록 높은 우선도)
)