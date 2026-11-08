package com.kobe.smels.controller.student

import com.kobe.smels.dto.student.CreateGuardianRequest
import com.kobe.smels.dto.student.GuardianResponse
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.UpdateGuardianRequest
import com.kobe.smels.service.student.GuardianService
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
@RequestMapping("/api/v1/guardians")
class GuardianController(
    private val guardianService: GuardianService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateGuardianRequest): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.create(it, request) }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.getById(it, id, schoolId) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<PageResponse<GuardianResponse>> =
        SecuritySupport.currentUser().flatMap { guardianService.list(it, page, size, active, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateGuardianRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<GuardianResponse> =
        SecuritySupport.currentUser().flatMap { guardianService.reactivate(it, id, schoolId) }
}
