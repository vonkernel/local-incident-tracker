package com.vonkernel.lit.collector.domain.exception

class CollectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
