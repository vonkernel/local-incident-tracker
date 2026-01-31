package com.vonkernel.lit.searcher.domain.exception

class InvalidSearchRequestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
