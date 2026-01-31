package com.vonkernel.lit.searcher.adapter.inbound.http

import com.vonkernel.lit.searcher.domain.exception.ArticleSearchException
import com.vonkernel.lit.searcher.domain.exception.InvalidSearchRequestException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidSearchRequestException::class)
    fun handleInvalidSearchRequest(e: InvalidSearchRequestException): ResponseEntity<Map<String, String>> {
        log.warn("Invalid search request: {}", e.message)
        return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
    }

    @ExceptionHandler(ArticleSearchException::class)
    fun handleArticleSearchException(e: ArticleSearchException): ResponseEntity<Map<String, String>> {
        log.error("Search failed: {}", e.message, e)
        return ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Search failed")))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unexpected error: {}", e.message, e)
        return ResponseEntity.internalServerError().body(mapOf("error" to "Internal server error"))
    }
}
