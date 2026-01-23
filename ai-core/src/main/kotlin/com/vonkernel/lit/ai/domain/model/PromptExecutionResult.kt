package com.vonkernel.lit.ai.domain.model

/**
 * LLM 프롬프트 실행 결과
 *
 * @param O 출력 데이터 타입
 * @property result LLM 응답을 역직렬화한 결과 객체
 * @property metadata 실행 메타데이터 (토큰 사용량, 모델 정보 등)
 */
data class PromptExecutionResult<O>(
    val result: O,
    val metadata: ExecutionMetadata
)

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
