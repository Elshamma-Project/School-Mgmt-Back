package com.kobe.smels.exception

open class BusinessException(
    val code: String,
    override val message: String,
    val status: Int = 400
) : RuntimeException(message)

class NotFoundException(code: String = "NOT_FOUND", message: String = "Resource not found") :
    BusinessException(code, message, 404)

class ConflictException(code: String = "CONFLICT", message: String = "Conflict") :
    BusinessException(code, message, 409)

class ForbiddenException(code: String = "FORBIDDEN", message: String = "Forbidden") :
    BusinessException(code, message, 403)

class UnauthorizedException(code: String = "UNAUTHORIZED", message: String = "Unauthorized") :
    BusinessException(code, message, 401)

class BusinessRuleException(code: String, message: String) :
    BusinessException(code, message, 422)
