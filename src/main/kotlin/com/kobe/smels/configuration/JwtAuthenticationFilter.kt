package com.kobe.smels.configuration

import com.kobe.smels.exception.UnauthorizedException
import com.kobe.smels.service.user.JwtService
import com.kobe.smels.service.user.UserSessionService
import com.kobe.smels.shared.security.CurrentUser
import com.kobe.smels.shared.security.SmelsAuthentication
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Valide le Bearer JWT et vérifie que la session associée est encore ACTIVE
 * (révocation, limite de sessions, logout).
 *
 * Ne pas exposer comme bean [WebFilter] (sinon double enregistrement Boot + Security).
 * Utiliser [asWebFilter] uniquement via [SecurityConfig.addFilterAt].
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userSessionService: UserSessionService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val passwordChangeAllowed = listOf(
        "/api/v1/auth/change-password",
        "/api/v1/auth/logout",
        "/api/v1/auth/logout-all",
        "/api/v1/auth/me",
        "/api/v1/auth/me/avatar",
        "/api/v1/auth/sessions"
    )

    fun asWebFilter(): WebFilter = WebFilter { exchange, chain -> filter(exchange, chain) }

    private fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ", ignoreCase = true)) {
            return chain.filter(exchange)
        }
        val rawToken = header.substring(7).trim()
        if (rawToken.isEmpty()) {
            return chain.filter(exchange)
        }

        val path = exchange.request.path.pathWithinApplication().value()
        return Mono.fromCallable { jwtService.parseAccessToken(rawToken) }
            .flatMap { currentUser ->
                userSessionService.requireActiveSession(currentUser.sessionId, currentUser.accountId)
                    .map { currentUser }
            }
            .flatMap { currentUser ->
                attachLogContext(exchange, currentUser)
                if (currentUser.mustChangePassword && passwordChangeAllowed.none { path.startsWith(it) }) {
                    log.warn(
                        "JWT accepted but password change required user={} path={}",
                        currentUser.username,
                        path
                    )
                    writePasswordChangeRequired(exchange)
                } else {
                    log.debug(
                        "JWT ok user={} accountId={} schoolId={} roles={} sessionId={} path={}",
                        currentUser.username,
                        currentUser.accountId,
                        currentUser.schoolId,
                        currentUser.roles.joinToString(","),
                        currentUser.sessionId,
                        path
                    )
                    val authentication = SmelsAuthentication(currentUser)
                    chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                }
            }
            .onErrorResume { error ->
                val code = (error as? UnauthorizedException)?.code ?: "INVALID_TOKEN"
                val detail = error.message?.takeIf { it.isNotBlank() }
                    ?: "Access token is invalid or expired"
                log.debug("JWT rejected path={} code={} reason={}", path, code, detail)
                writeUnauthorized(exchange, code, detail)
            }
    }

    private fun attachLogContext(exchange: ServerWebExchange, currentUser: CurrentUser) {
        exchange.attributes[RequestLoggingWebFilter.ATTR_USERNAME] = currentUser.username
        exchange.attributes[RequestLoggingWebFilter.ATTR_ACCOUNT_ID] = currentUser.accountId
        exchange.attributes[RequestLoggingWebFilter.ATTR_SCHOOL_ID] = currentUser.schoolId ?: "-"
        exchange.attributes[RequestLoggingWebFilter.ATTR_ROLES] =
            currentUser.roles.joinToString(",") { it.name }
    }

    private fun writePasswordChangeRequired(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val body =
            """{"type":"urn:smels:error:password_change_required","title":"PASSWORD_CHANGE_REQUIRED","status":403,"detail":"Password change required before continuing","code":"PASSWORD_CHANGE_REQUIRED"}"""
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.toByteArray())))
    }

    private fun writeUnauthorized(exchange: ServerWebExchange, code: String, detail: String): Mono<Void> {
        val response = exchange.response
        if (response.isCommitted) {
            return Mono.empty()
        }
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val safeDetail = detail.replace("\"", "'")
        val safeCode = code.replace("\"", "'")
        val json =
            """{"type":"urn:smels:error:${safeCode.lowercase()}","title":"$safeCode","status":401,"detail":"$safeDetail","code":"$safeCode","instance":"${exchange.request.path.value()}"}"""
        return response.writeWith(Mono.just(response.bufferFactory().wrap(json.toByteArray())))
    }
}
