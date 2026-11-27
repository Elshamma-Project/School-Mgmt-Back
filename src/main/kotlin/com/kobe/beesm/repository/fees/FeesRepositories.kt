package com.kobe.beesm.repository.fees

import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.FeePenaltyCharge
import com.kobe.beesm.model.fees.FeeStudentFeeState
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.fees.TuitionScheduleDefaults
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TuitionScheduleDefaultsRepository : ReactiveMongoRepository<TuitionScheduleDefaults, String> {
    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String
    ): Mono<TuitionScheduleDefaults>
}

interface FeesPolicyRepository : ReactiveMongoRepository<FeesPolicy, String> {
    fun findBySchoolId(schoolId: String): Mono<FeesPolicy>

    fun findByActiveTrue(): Flux<FeesPolicy>
}

interface FeeStudentFeeStateRepository : ReactiveMongoRepository<FeeStudentFeeState, String> {
    fun findBySchoolIdAndAcademicYearIdAndStudentId(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<FeeStudentFeeState>

    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String
    ): Flux<FeeStudentFeeState>
}

interface FeePenaltyChargeRepository : ReactiveMongoRepository<FeePenaltyCharge, String> {
    fun findBySchoolIdAndAcademicYearIdAndStudentId(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Flux<FeePenaltyCharge>
}

interface TuitionScheduleRepository : ReactiveMongoRepository<TuitionSchedule, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearIdAndLevelId(
        schoolId: String,
        academicYearId: String,
        levelId: String
    ): Mono<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearIdAndActiveTrue(
        schoolId: String,
        academicYearId: String
    ): Flux<TuitionSchedule>

    fun findBySchoolIdAndAcademicYearId(
        schoolId: String,
        academicYearId: String
    ): Flux<TuitionSchedule>

    fun findByActiveTrue(): Flux<TuitionSchedule>
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

    fun findBySchoolIdAndAcademicYearIdAndStatus(
        schoolId: String,
        academicYearId: String,
        status: FeePaymentStatus
    ): Flux<FeePayment>
}
