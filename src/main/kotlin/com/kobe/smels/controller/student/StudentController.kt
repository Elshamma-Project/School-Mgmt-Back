package com.kobe.smels.controller.student

import com.kobe.smels.dto.student.CreateStudentRequest
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.StudentResponse
import com.kobe.smels.dto.student.UpdateStudentRequest
import com.kobe.smels.service.student.StudentService
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
@RequestMapping("/api/v1/students")
class StudentController(
    private val studentService: StudentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateStudentRequest): Mono<StudentResponse> =
        SecuritySupport.currentUser().flatMap { studentService.create(it, request) }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StudentResponse> =
        SecuritySupport.currentUser().flatMap { studentService.getById(it, id, schoolId) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<PageResponse<StudentResponse>> =
        SecuritySupport.currentUser().flatMap { studentService.list(it, page, size, active, q, schoolId) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateStudentRequest,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StudentResponse> =
        SecuritySupport.currentUser().flatMap { studentService.update(it, id, request, schoolId) }

    @PostMapping("/{id}/deactivate")
    fun deactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StudentResponse> =
        SecuritySupport.currentUser().flatMap { studentService.deactivate(it, id, schoolId) }

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<StudentResponse> =
        SecuritySupport.currentUser().flatMap { studentService.reactivate(it, id, schoolId) }
}
