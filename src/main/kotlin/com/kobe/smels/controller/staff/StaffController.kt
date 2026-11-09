package com.kobe.smels.controller.staff

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.dto.staff.CreateStaffRequest
import com.kobe.smels.dto.staff.PageResponse
import com.kobe.smels.dto.staff.StaffResponse
import com.kobe.smels.dto.staff.UpdateStaffIdentityRequest
import com.kobe.smels.dto.staff.UpdateStaffRequest
import com.kobe.smels.dto.user.CreateUserAccountRequest
import com.kobe.smels.dto.user.StaffLinkedAccountDto
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.service.staff.StaffService
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.security.SecuritySupport
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
@RequestMapping("/api/v1/staff")
class StaffController(
    private val staffService: StaffService,
    private val userAccountApi: UserAccountApi,
    private val securityProperties: SecurityProperties
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateStaffRequest): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { currentUser ->
            val wantLogin = request.createLoginAccount
            val email = request.contact.email?.trim()?.lowercase()?.ifBlank { null }

            if (wantLogin && email == null) {
                return@flatMap Mono.error(
                    BusinessRuleException(
                        "EMAIL_REQUIRED",
                        "Email is required to create a login account"
                    )
                )
            }

            staffService.create(currentUser, request).flatMap { staff ->
                if (!wantLogin) {
                    Mono.just(staff)
                } else {
                    val temporaryPassword = securityProperties.provisioning.temporaryPassword
                    userAccountApi
                        .createAccount(
                            currentUser,
                            CreateUserAccountRequest(
                                username = email!!,
                                password = temporaryPassword,
                                schoolId = staff.schoolId,
                                roles = StaffLoginRoles.fromFunctions(staff.functions),
                                profileType = ProfileType.STAFF,
                                profileId = staff.id,
                                mustChangePassword = true
                            )
                        )
                        .map { account ->
                            staff.copy(
                                hasLoginAccount = true,
                                loginUsername = account.username,
                                temporaryPassword = temporaryPassword
                            )
                        }
                }
            }
        }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser()
            .flatMap { staffService.getById(it, id, schoolId) }
            .flatMap { enrichOne(it) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) function: StaffFunction?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<PageResponse<StaffResponse>> =
        SecuritySupport.currentUser()
            .flatMap { staffService.list(it, page, size, active, function, q, schoolId) }
            .flatMap { enrichPage(it) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateStaffRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.update(it, id, request, schoolId) }

    /** Identité limitée : soi-même (fiche liée) ou ADMINISTRATION / DIRECTOR / SUPER_ADMIN. */
    @PutMapping("/{id}/identity")
    fun updateIdentity(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateStaffIdentityRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.updateIdentity(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.reactivate(it, id, schoolId) }

    private fun enrichPage(page: PageResponse<StaffResponse>): Mono<PageResponse<StaffResponse>> {
        if (page.content.isEmpty()) {
            return Mono.just(page)
        }
        val schoolId = page.content.first().schoolId
        val ids = page.content.map { it.id }
        return userAccountApi.findStaffLinkedAccounts(schoolId, ids).map { accounts ->
            page.copy(content = page.content.map { applyLinkedAccount(it, accounts) })
        }
    }

    private fun enrichOne(staff: StaffResponse): Mono<StaffResponse> =
        userAccountApi.findStaffLinkedAccounts(staff.schoolId, listOf(staff.id)).map { accounts ->
            applyLinkedAccount(staff, accounts)
        }

    private fun applyLinkedAccount(
        staff: StaffResponse,
        accounts: Map<String, StaffLinkedAccountDto>
    ): StaffResponse {
        val linked = accounts[staff.id]
        return staff.copy(
            lastLoginAt = linked?.lastLoginAt ?: staff.lastLoginAt,
            hasLoginAccount = linked != null,
            loginUsername = linked?.username ?: staff.loginUsername
        )
    }
}
