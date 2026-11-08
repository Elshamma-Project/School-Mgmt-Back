package com.kobe.smels.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ProblemDetail {
        val status = HttpStatus.valueOf(ex.status)
        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = ex.code
            type = URI.create("urn:smels:error:${ex.code.lowercase()}")
            setProperty("code", ex.code)
        }
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(ex: WebExchangeBindException): ProblemDetail {
        val detail = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validation failed" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).apply {
            title = "VALIDATION_ERROR"
            type = URI.create("urn:smels:error:validation")
            setProperty("code", "VALIDATION_ERROR")
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error").apply {
            title = "INTERNAL_ERROR"
            type = URI.create("urn:smels:error:internal")
            setProperty("code", "INTERNAL_ERROR")
        }
    }
}
