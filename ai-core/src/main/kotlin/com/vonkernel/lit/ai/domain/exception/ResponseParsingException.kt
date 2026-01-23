package com.vonkernel.lit.ai.domain.exception

/**
 * LLM 응답을 파싱/역직렬화하는데 실패한 경우
 *
 * @property promptId 응답 파싱 실패한 프롬프트 ID
 * @property responseContent LLM의 원본 응답 내용
 * @property targetType 역직렬화하려던 타입
 */
class ResponseParsingException(
    val promptId: String,
    val responseContent: String,
    val targetType: String,
    message: String,
    cause: Throwable? = null
) : AiCoreException("Failed to parse LLM response for prompt '$promptId' into $targetType: $message", cause)
