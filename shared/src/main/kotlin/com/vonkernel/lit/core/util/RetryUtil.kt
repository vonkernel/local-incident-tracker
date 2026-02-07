package com.vonkernel.lit.core.util

import com.vonkernel.lit.core.exception.MaxRetriesExceededException
import kotlinx.coroutines.delay
import kotlin.math.pow


/**
 * 재시도 로직을 수행하는 고차 함수
 *
 * @param maxRetries 최대 재시도 횟수
 * @param baseDelayMs 기본 지연 시간 (ms)
 * @param backoffFactor 지수 백오프 계수
 * @param onRetry 재시도 발생 시 호출되는 콜백 (호출자와의 커뮤니케이션 채널)
 * @param block 실행할 작업
 */
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    baseDelayMs: Long = 1000L,
    backoffFactor: Double = 2.0,
    onRetry: suspend (attempt: Int, nextDelay: Long, exception: Throwable) -> Unit = { _, _, _ -> },
    block: suspend () -> T
): T = retryImpl(
    attempt = 1,
    maxRetries = maxRetries,
    baseDelayMs = baseDelayMs,
    backoffFactor = backoffFactor,
    onRetry = onRetry,
    block = block
)

private suspend fun <T> retryImpl(
    attempt: Int,
    maxRetries: Int,
    baseDelayMs: Long,
    backoffFactor: Double,
    onRetry: suspend (Int, Long, Throwable) -> Unit,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (e: Exception) {
        if (attempt > maxRetries) {
            throw MaxRetriesExceededException("Failed after $maxRetries attempts", e)
        }
        val nextDelay = (baseDelayMs * backoffFactor.pow(attempt - 1)).toLong()
        onRetry(attempt, nextDelay, e)
        delay(nextDelay)
        retryImpl(attempt + 1, maxRetries, baseDelayMs, backoffFactor, onRetry, block)
    }