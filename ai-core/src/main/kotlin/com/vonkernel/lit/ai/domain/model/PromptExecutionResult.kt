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
