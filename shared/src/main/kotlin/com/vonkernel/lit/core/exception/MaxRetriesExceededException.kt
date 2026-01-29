package com.vonkernel.lit.core.exception

class MaxRetriesExceededException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
