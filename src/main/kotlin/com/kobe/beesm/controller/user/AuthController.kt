package com.kobe.beesm.controller.user

import com.kobe.beesm.dto.user.AuthTokensResponse
import com.kobe.beesm.dto.user.ChangePasswordRequest
import com.kobe.beesm.dto.user.ForgotPasswordRequest
import com.kobe.beesm.dto.user.ForgotPasswordResponse
import com.kobe.beesm.dto.user.LoginRequest
import com.kobe.beesm.dto.user.RefreshTokenRequest
import com.kobe.beesm.dto.user.SessionResponse
import com.kobe.beesm.dto.user.UserAccountResponse
import com.kobe.beesm.configuration.LoginRateLimiter
import com.kobe.beesm.service.user.AuthService
import com.kobe.beesm.shared.security.SecuritySupport
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

    /** Public — même réponse que le compte existe ou non (anti-énumération). */
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        httpRequest: ServerHttpRequest
    ): Mono<ForgotPasswordResponse> {
        val ip = clientIp(httpRequest)
        loginRateLimiter.check(ip)
        return authService.requestPasswordReset(request)
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

    /** Liste JSON classique (`[{...}]`) — pas un stream Flux (le front attend un tableau). */
    @GetMapping("/sessions")
    fun sessions(): Mono<List<SessionResponse>> =
        SecuritySupport.currentUser()
            .flatMap { authService.listSessions(it).collectList() }

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
                // URL identique pour tous les comptes (/me/avatar) : ne jamais laisser
                // le navigateur resservir la photo du compte précédent (max-age + même URL).
                ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(avatar.contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
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
