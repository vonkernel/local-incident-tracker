package com.vonkernel.lit.core.util

import com.vonkernel.lit.core.exception.MaxRetriesExceededException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("RetryUtil 테스트")
class RetryUtilTest {

    @Test
    @DisplayName("첫 시도에 성공하면 바로 반환")
    fun `첫 시도에 성공하면 바로 반환`() = runTest {
        val result = executeWithRetry(
            maxRetries = 3,
            baseDelayMs = 1L,
        ) {
            "success"
        }

        assertThat(result).isEqualTo("success")
    }

    @Test
    @DisplayName("재시도 후 성공")
    fun `재시도 후 성공`() = runTest {
        var callCount = 0

        val result = executeWithRetry(
            maxRetries = 3,
            baseDelayMs = 1L,
        ) {
            callCount++
            if (callCount < 2) {
                throw RuntimeException("일시적 오류")
            }
            "success after retry"
        }

        assertThat(result).isEqualTo("success after retry")
        assertThat(callCount).isEqualTo(2)
    }

    @Test
    @DisplayName("최대 재시도 초과 시 MaxRetriesExceededException 발생")
    fun `최대 재시도 초과 시 MaxRetriesExceededException 발생`() = runTest {
        var callCount = 0

        val exception = assertThrows<MaxRetriesExceededException> {
            executeWithRetry(
                maxRetries = 3,
                baseDelayMs = 1L,
            ) {
                callCount++
                throw RuntimeException("항상 실패")
            }
        }

        assertThat(exception.message).isEqualTo("Failed after 3 attempts")
        assertThat(callCount).isEqualTo(4) // 최초 1회 + 재시도 3회
    }

    @Test
    @DisplayName("onRetry 콜백이 올바른 인자로 호출됨")
    fun `onRetry 콜백이 올바른 인자로 호출됨`() = runTest {
        val retryAttempts = mutableListOf<Int>()
        val retryExceptions = mutableListOf<Throwable>()
        var callCount = 0

        executeWithRetry(
            maxRetries = 3,
            baseDelayMs = 1L,
            backoffFactor = 2.0,
            onRetry = { attempt, _, exception ->
                retryAttempts.add(attempt)
                retryExceptions.add(exception)
            },
        ) {
            callCount++
            if (callCount < 3) {
                throw RuntimeException("오류 #$callCount")
            }
            "success"
        }

        assertThat(retryAttempts).containsExactly(1, 2)
        assertThat(retryExceptions).hasSize(2)
        assertThat(retryExceptions[0].message).isEqualTo("오류 #1")
        assertThat(retryExceptions[1].message).isEqualTo("오류 #2")
    }

    @Test
    @DisplayName("maxRetries가 0이면 재시도 없이 즉시 실패")
    fun `maxRetries가 0이면 재시도 없이 즉시 실패`() = runTest {
        var callCount = 0

        val exception = assertThrows<MaxRetriesExceededException> {
            executeWithRetry(
                maxRetries = 0,
                baseDelayMs = 1L,
            ) {
                callCount++
                throw RuntimeException("즉시 실패")
            }
        }

        assertThat(callCount).isEqualTo(1)
        assertThat(exception.message).isEqualTo("Failed after 0 attempts")
    }

    @Test
    @DisplayName("원본 예외가 cause로 전달됨")
    fun `원본 예외가 cause로 전달됨`() = runTest {
        val originalException = IllegalStateException("원본 오류")

        val exception = assertThrows<MaxRetriesExceededException> {
            executeWithRetry(
                maxRetries = 1,
                baseDelayMs = 1L,
            ) {
                throw originalException
            }
        }

        assertThat(exception.cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception.cause?.message).isEqualTo("원본 오류")
    }
}
