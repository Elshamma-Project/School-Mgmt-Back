package com.kobe.smels.repository.reporting

import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.reporting.StudentPeriodReview
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ReportTemplateRepository : ReactiveMongoRepository<ReportTemplate, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<ReportTemplate>
    fun findBySchoolIdAndCode(schoolId: String, code: String): Mono<ReportTemplate>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<ReportTemplate>
    fun findBySchoolId(schoolId: String): Flux<ReportTemplate>
}

interface ReportCardRepository : ReactiveMongoRepository<ReportCard, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<ReportCard>
    fun findBySchoolIdAndVerificationCode(schoolId: String, verificationCode: String): Mono<ReportCard>
    fun findByVerificationCode(verificationCode: String): Mono<ReportCard>
    fun findBySchoolIdAndClassIdAndPeriod_TypeAndPeriod_NumberAndStatus(
        schoolId: String,
        classId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?,
        status: ReportCardStatus
    ): Flux<ReportCard>
    fun findBySchoolIdAndEnrollmentIdAndPeriod_TypeAndPeriod_NumberOrderByRevisionDesc(
        schoolId: String,
        enrollmentId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?
    ): Flux<ReportCard>
    fun findBySchoolIdAndStudentIdAndPeriod_TypeAndStatus(
        schoolId: String,
        studentId: String,
        periodType: ReportPeriodType,
        status: ReportCardStatus
    ): Flux<ReportCard>
}

interface StudentPeriodReviewRepository : ReactiveMongoRepository<StudentPeriodReview, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<StudentPeriodReview>
    fun findBySchoolIdAndEnrollmentIdAndPeriod_TypeAndPeriod_Number(
        schoolId: String,
        enrollmentId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?
    ): Mono<StudentPeriodReview>
}
