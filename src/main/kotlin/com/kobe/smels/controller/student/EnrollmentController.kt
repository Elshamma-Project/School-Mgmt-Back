package com.kobe.smels.controller.student

import com.kobe.smels.dto.student.CreateEnrollmentRequest
import com.kobe.smels.dto.student.EnrollmentResponse
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.UpdateEnrollmentRequest
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.service.student.EnrollmentService
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
@RequestMapping("/api/v1/enrollments")
class EnrollmentController(
    private val enrollmentService: EnrollmentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateEnrollmentRequest): Mono<EnrollmentResponse> =
        SecuritySupport.currentUser().flatMap { enrollmentService.create(it, request) }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<EnrollmentResponse> =
        SecuritySupport.currentUser().flatMap { enrollmentService.getById(it, id, schoolId) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) academicYearId: String?,
        @RequestParam(required = false) classId: String?,
        @RequestParam(required = false) status: EnrollmentStatus?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<PageResponse<EnrollmentResponse>> =
        SecuritySupport.currentUser().flatMap {
            enrollmentService.list(it, page, size, academicYearId, classId, status, schoolId)
        }

    @GetMapping("/by-student/{studentId}")
    fun listByStudent(
        @PathVariable studentId: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<List<EnrollmentResponse>> =
        SecuritySupport.currentUser().flatMap { enrollmentService.listByStudent(it, studentId, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateEnrollmentRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<EnrollmentResponse> =
        SecuritySupport.currentUser().flatMap { enrollmentService.update(it, id, request, schoolId) }
}
