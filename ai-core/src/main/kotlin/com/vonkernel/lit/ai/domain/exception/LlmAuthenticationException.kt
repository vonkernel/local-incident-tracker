package com.vonkernel.lit.ai.domain.exception

/**
 * LLM API 인증 실패 (잘못된 API 키 등)
 */
class LlmAuthenticationException(
    message: String = "LLM API authentication failed. Please check your API key."
) : LlmExecutionException(message)
