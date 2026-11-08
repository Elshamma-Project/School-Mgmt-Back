package com.kobe.smels.repository.student

import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface EnrollmentRepository : ReactiveMongoRepository<Enrollment, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Enrollment>
    fun findBySchoolIdAndStudentIdAndAcademicYearId(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<Enrollment>

    fun findBySchoolIdAndClassIdAndStatus(
        schoolId: String,
        classId: String,
        status: EnrollmentStatus,
        pageable: Pageable
    ): Flux<Enrollment>

    fun countBySchoolIdAndClassIdAndStatus(
        schoolId: String,
        classId: String,
        status: EnrollmentStatus
    ): Mono<Long>

    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String,
        pageable: Pageable
    ): Flux<Enrollment>

    fun countBySchoolIdAndAcademicYearId(schoolId: String, academicYearId: String): Mono<Long>

    fun findBySchoolIdAndStudentId(schoolId: String, studentId: String): Flux<Enrollment>

    fun findBySchoolIdAndClassIdAndStatus(
        schoolId: String,
        classId: String,
        status: EnrollmentStatus
    ): Flux<Enrollment>
}
