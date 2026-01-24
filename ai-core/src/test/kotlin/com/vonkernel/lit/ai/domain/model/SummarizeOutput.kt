package com.vonkernel.lit.ai.domain.model

/**
 * 요약 프롬프트 테스트용 출력 DTO
 *
 * summarize.yml 프롬프트의 출력 타입으로 사용
 * LLM이 JSON 형식으로 반환하는 응답 구조
 */
data class SummarizeOutput(
    val summary: String,
    val keyPoints: List<String>
)
