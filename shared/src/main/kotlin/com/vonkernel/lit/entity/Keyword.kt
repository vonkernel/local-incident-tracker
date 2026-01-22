package com.vonkernel.lit.entity

/**
 * 분석된 키워드와 그 중요도를 나타내는 데이터 클래스
 */
data class Keyword(
    val keyword: String,   // 키워드 텍스트
    val priority: Int      // 우선도 (높을수록 중요함)
)