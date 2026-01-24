package com.vonkernel.lit.ai.domain.model

/**
 * 요약 프롬프트 테스트용 입력 DTO
 *
 * summarize.yml 프롬프트의 입력 타입으로 사용
 */
data class SummarizeInput(
    val text: String,
    val maxLength: Int = 100
)
