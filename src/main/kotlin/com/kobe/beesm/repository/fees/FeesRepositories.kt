package com.kobe.beesm.repository.fees

import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.TuitionSchedule
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TuitionScheduleRepository : ReactiveMongoRepository<TuitionSchedule, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearIdAndClassId(
        schoolId: String,
        academicYearId: String,
        classId: String
    ): Mono<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearIdAndActiveTrue(
        schoolId: String,
        academicYearId: String
    ): Flux<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String
    ): Flux<TuitionSchedule>
}

interface FeePaymentRepository : ReactiveMongoRepository<FeePayment, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<FeePayment>

    fun findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
        schoolId: String,
        academicYearId: String,
        studentId: String,
        status: FeePaymentStatus
    ): Flux<FeePayment>

    fun findBySchoolIdAndAcademicYearIdAndStudentIdInAndStatus(
        schoolId: String,
        academicYearId: String,
        studentIds: Collection<String>,
        status: FeePaymentStatus
    ): Flux<FeePayment>
}
