package com.vonkernel.lit.ai.domain.exception

/**
 * LLM 실행 관련 예외의 상위 클래스
 */
sealed class LlmExecutionException(
    message: String,
    cause: Throwable? = null
) : AiCoreException(message, cause)
