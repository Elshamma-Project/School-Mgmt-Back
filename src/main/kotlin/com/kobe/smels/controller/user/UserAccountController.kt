package com.kobe.smels.controller.user

import com.kobe.smels.dto.user.CreateUserAccountRequest
import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.service.user.UserAccountService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

data class ResetPasswordRequest(
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val temporaryPassword: String
)

@RestController
@RequestMapping("/api/v1/users")
class UserAccountController(
    private val userAccountService: UserAccountService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateUserAccountRequest): Mono<UserAccountResponse> =
        SecuritySupport.currentUser().flatMap { userAccountService.create(it, request) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): Mono<UserAccountResponse> =
        SecuritySupport.currentUser().flatMap { userAccountService.getById(it, id) }

    @PostMapping("/{id}/disable")
    fun disable(@PathVariable id: String): Mono<UserAccountResponse> =
        SecuritySupport.currentUser().flatMap { userAccountService.disable(it, id) }

    @PostMapping("/{id}/reset-password")
    fun resetPassword(
        @PathVariable id: String,
        @Valid @RequestBody request: ResetPasswordRequest
    ): Mono<UserAccountResponse> =
        SecuritySupport.currentUser()
            .flatMap { userAccountService.resetPassword(it, id, request.temporaryPassword) }
}
