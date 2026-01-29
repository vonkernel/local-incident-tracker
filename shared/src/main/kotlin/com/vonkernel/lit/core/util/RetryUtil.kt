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
): T {
    var currentAttempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            currentAttempt++

            // 재시도 횟수 초과 시 예외를 그대로 전파 (또는 커스텀 예외로 래핑 가능)
            if (currentAttempt > maxRetries) {
                throw MaxRetriesExceededException("Failed to fetch page after $maxRetries attempts", e)
            }

            // 백오프 시간 계산
            val nextDelay = (baseDelayMs * backoffFactor.pow(currentAttempt - 1)).toLong()

            // [커뮤니케이션] 호출자에게 재시도 상황 알림 (로깅 등은 여기서 수행됨)
            onRetry(currentAttempt, nextDelay, e)

            delay(nextDelay)
        }
    }
}