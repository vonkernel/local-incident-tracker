package com.vonkernel.lit.ai.domain.model

/**
 * 프롬프트 실행 메타데이터
 *
 * @property model 실행된 모델 정보
 * @property tokenUsage 토큰 사용량 정보
 * @property finishReason 완료 사유 (예: "stop", "length", "content_filter")
 */
data class ExecutionMetadata(
    val model: String,
    val tokenUsage: TokenUsage,
    val finishReason: String?
)
