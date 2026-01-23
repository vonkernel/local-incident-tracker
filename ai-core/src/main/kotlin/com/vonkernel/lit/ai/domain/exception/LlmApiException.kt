package com.vonkernel.lit.ai.domain.exception

/**
 * LLM API 호출 실패 (HTTP 에러, 네트워크 오류 등)
 *
 * @property statusCode HTTP 상태 코드 (있을 경우)
 * @property errorBody API 응답 본문 (있을 경우)
 */
class LlmApiException(
    val statusCode: Int?,
    val errorBody: String?,
    message: String,
    cause: Throwable? = null
) : LlmExecutionException("LLM API error${statusCode?.let { " [$it]" } ?: ""}: $message", cause)
