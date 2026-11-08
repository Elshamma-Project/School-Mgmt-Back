package com.kobe.smels.configuration

import com.kobe.smels.service.user.JwtService
import com.kobe.smels.service.user.UserSessionService
import com.kobe.smels.shared.security.CurrentUser
import com.kobe.smels.shared.security.SmelsAuthentication
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
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userSessionService: UserSessionService
) : WebFilter {

    private val passwordChangeAllowed = listOf(
        "/api/v1/auth/change-password",
        "/api/v1/auth/logout",
        "/api/v1/auth/logout-all",
        "/api/v1/auth/me",
        "/api/v1/auth/sessions"
    )

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ", ignoreCase = true)) {
            return chain.filter(exchange)
        }
        val rawToken = header.substring(7).trim()
        if (rawToken.isEmpty()) {
            return chain.filter(exchange)
        }

        return Mono.fromCallable { jwtService.parseAccessToken(rawToken) }
            .flatMap { currentUser ->
                userSessionService.requireActiveSession(currentUser.sessionId, currentUser.accountId)
                    .map { currentUser }
            }
            .flatMap { currentUser ->
                val path = exchange.request.path.pathWithinApplication().value()
                if (currentUser.mustChangePassword && passwordChangeAllowed.none { path.startsWith(it) }) {
                    writePasswordChangeRequired(exchange)
                } else {
                    val authentication = SmelsAuthentication(currentUser)
                    chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                }
            }
            .onErrorResume { chain.filter(exchange) }
    }

    private fun writePasswordChangeRequired(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val body =
            """{"type":"urn:smels:error:password_change_required","title":"PASSWORD_CHANGE_REQUIRED","status":403,"detail":"Password change required before continuing","code":"PASSWORD_CHANGE_REQUIRED"}"""
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.toByteArray())))
    }
}
