package com.vonkernel.lit.exception

class MaxRetriesExceededException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
