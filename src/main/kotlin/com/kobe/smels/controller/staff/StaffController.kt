package com.kobe.smels.controller.staff

import com.kobe.smels.dto.staff.CreateStaffRequest
import com.kobe.smels.dto.staff.PageResponse
import com.kobe.smels.dto.staff.StaffResponse
import com.kobe.smels.dto.staff.UpdateStaffRequest
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.service.staff.StaffService
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
    private val staffService: StaffService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateStaffRequest): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.create(it, request) }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.getById(it, id, schoolId) }

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

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateStaffRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StaffResponse> =
        SecuritySupport.currentUser().flatMap { staffService.update(it, id, request, schoolId) }

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
}
