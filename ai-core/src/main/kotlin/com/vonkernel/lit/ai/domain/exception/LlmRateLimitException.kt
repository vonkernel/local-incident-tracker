package com.vonkernel.lit.ai.domain.exception

/**
 * LLM API Rate Limit 초과
 *
 * @property retryAfterSeconds 재시도 가능 시간 (초, 있을 경우)
 */
class LlmRateLimitException(
    val retryAfterSeconds: Int?,
    message: String = "LLM API rate limit exceeded${retryAfterSeconds?.let { ". Retry after $it seconds" } ?: ""}"
) : LlmExecutionException(message)
