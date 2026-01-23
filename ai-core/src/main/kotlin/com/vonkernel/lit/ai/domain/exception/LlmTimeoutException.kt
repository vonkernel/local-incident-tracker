package com.vonkernel.lit.ai.domain.exception

/**
 * LLM API 타임아웃
 *
 * @property timeoutMs 타임아웃 시간 (밀리초)
 */
class LlmTimeoutException(
    val timeoutMs: Long,
    message: String = "LLM API call timed out after ${timeoutMs}ms"
) : LlmExecutionException(message)
