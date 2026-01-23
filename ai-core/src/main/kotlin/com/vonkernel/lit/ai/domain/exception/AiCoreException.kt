package com.vonkernel.lit.ai.domain.exception

/**
 * ai-core 모듈의 최상위 예외 클래스
 *
 * Sealed class로 정의하여 컴파일 타임에 모든 예외 유형을 파악 가능
 */
sealed class AiCoreException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
