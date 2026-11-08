package com.kobe.smels.controller.user

import com.kobe.smels.dto.user.AuthTokensResponse
import com.kobe.smels.dto.user.ChangePasswordRequest
import com.kobe.smels.dto.user.LoginRequest
import com.kobe.smels.dto.user.RefreshTokenRequest
import com.kobe.smels.dto.user.SessionResponse
import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.configuration.LoginRateLimiter
import com.kobe.smels.service.user.AuthService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val loginRateLimiter: LoginRateLimiter
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: ServerHttpRequest
    ): Mono<AuthTokensResponse> {
        val ip = clientIp(httpRequest)
        loginRateLimiter.check(ip)
        return authService.login(request, userAgent(httpRequest), ip)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): Mono<AuthTokensResponse> =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    fun logout(): Mono<Void> =
        SecuritySupport.currentUser().flatMap { authService.logout(it) }

    @PostMapping("/logout-all")
    fun logoutAll(): Mono<Map<String, Long>> =
        SecuritySupport.currentUser()
            .flatMap { authService.logoutAll(it) }
            .map { mapOf("revokedSessions" to it) }

    @GetMapping("/me")
    fun me(): Mono<UserAccountResponse> =
        SecuritySupport.currentUser().flatMap { authService.me(it) }

    @GetMapping("/sessions")
    fun sessions(): Flux<SessionResponse> =
        SecuritySupport.currentUser().flatMapMany { authService.listSessions(it) }

    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(@PathVariable sessionId: String): Mono<Void> =
        SecuritySupport.currentUser().flatMap { authService.revokeSession(it, sessionId) }

    @PostMapping("/change-password")
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest): Mono<AuthTokensResponse> =
        SecuritySupport.currentUser().flatMap { authService.changePassword(it, request) }

    /** Photo de profil UI — jamais embarquée dans un bulletin. */
    @PutMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(@RequestPart("file") file: FilePart): Mono<UserAccountResponse> =
        SecuritySupport.currentUser().flatMap { authService.uploadAvatar(it, file) }

    @GetMapping("/me/avatar")
    fun getAvatar(): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser()
            .flatMap { authService.getAvatar(it) }
            .map { avatar ->
                ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(avatar.contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .body(avatar.data.data)
            }

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAvatar(): Mono<Void> =
        SecuritySupport.currentUser()
            .flatMap { authService.deleteAvatar(it) }
            .then()

    private fun userAgent(request: ServerHttpRequest): String? =
        request.headers.getFirst(HttpHeaders.USER_AGENT)

    private fun clientIp(request: ServerHttpRequest): String? {
        val forwarded = request.headers.getFirst("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddress?.address?.hostAddress
    }
}
