package com.kobe.beesm.controller.student

import com.kobe.beesm.dto.student.CreateGuardianRequest
import com.kobe.beesm.dto.student.GuardianResponse
import com.kobe.beesm.dto.student.PageResponse
import com.kobe.beesm.dto.student.UpdateGuardianRequest
import com.kobe.beesm.dto.user.StaffLinkedAccountDto
import com.kobe.beesm.service.student.GuardianService
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/guardians")
class GuardianController(
    private val guardianService: GuardianService,
    private val userAccountApi: UserAccountApi
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateGuardianRequest): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.create(it, request) }
            .flatMap { enrichOne(it) }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.getById(it, id, schoolId) }
            .flatMap { enrichOne(it) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<PageResponse<GuardianResponse>> =
        SecuritySupport.currentUser().flatMap { guardianService.list(it, page, size, active, schoolId) }
            .flatMap { enrichPage(it) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateGuardianRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.update(it, id, request, schoolId) }
            .flatMap { enrichOne(it) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.deactivate(it, id, schoolId) }
            .flatMap { enrichOne(it) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.reactivate(it, id, schoolId) }
            .flatMap { enrichOne(it) }

    private fun enrichPage(page: PageResponse<GuardianResponse>): Mono<PageResponse<GuardianResponse>> {
        if (page.content.isEmpty()) {
            return Mono.just(page)
        }
        val schoolId = page.content.first().schoolId
        val ids = page.content.map { it.id }
        return userAccountApi.findGuardianLinkedAccounts(schoolId, ids).map { accounts ->
            page.copy(content = page.content.map { applyLinkedAccount(it, accounts) })
        }
    }

    private fun enrichOne(guardian: GuardianResponse): Mono<GuardianResponse> =
        userAccountApi.findGuardianLinkedAccounts(guardian.schoolId, listOf(guardian.id)).map { accounts ->
            applyLinkedAccount(guardian, accounts)
        }

    private fun applyLinkedAccount(
        guardian: GuardianResponse,
        accounts: Map<String, StaffLinkedAccountDto>
    ): GuardianResponse {
        val linked = accounts[guardian.id]
        return guardian.copy(
            hasLoginAccount = linked != null,
            loginUsername = linked?.username
        )
    }
}
