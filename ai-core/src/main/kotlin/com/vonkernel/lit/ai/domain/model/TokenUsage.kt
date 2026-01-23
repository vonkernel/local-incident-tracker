package com.vonkernel.lit.ai.domain.model

/**
 * LLM 실행 시 토큰 사용량 정보
 *
 * @property promptTokens 입력(프롬프트)에 사용된 토큰 수
 * @property completionTokens 출력(생성)에 사용된 토큰 수
 * @property totalTokens 총 토큰 수 (promptTokens + completionTokens)
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
