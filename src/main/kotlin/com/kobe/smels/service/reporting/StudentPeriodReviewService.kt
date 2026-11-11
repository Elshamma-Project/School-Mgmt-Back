package com.kobe.smels.service.reporting

import com.kobe.smels.dto.reporting.StudentPeriodReviewResponse
import com.kobe.smels.dto.reporting.UpsertStudentPeriodReviewRequest
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.reporting.ReportPeriod
import com.kobe.smels.model.reporting.StudentPeriodReview
import com.kobe.smels.repository.reporting.StudentPeriodReviewRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class StudentPeriodReviewService(
    private val studentPeriodReviewRepository: StudentPeriodReviewRepository,
    private val academicYearApi: AcademicYearApi
) {
    fun upsert(
        currentUser: CurrentUser,
        request: UpsertStudentPeriodReviewRequest
    ): Mono<StudentPeriodReviewResponse> {
        ReportingAccess.assertAdmin(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, request.schoolId)
        return academicYearApi.assertWritable(schoolId, request.academicYearId)
            .then(
                studentPeriodReviewRepository
                    .findBySchoolIdAndEnrollmentIdAndPeriod_TypeAndPeriod_Number(
                        schoolId,
                        request.enrollmentId,
                        request.periodType,
                        request.periodNumber
                    )
                    .flatMap { existing ->
                        existing.absenceHoursTotal = request.absenceHoursTotal
                        existing.absenceHoursUnjustified = request.absenceHoursUnjustified
                        existing.lateCount = request.lateCount
                        existing.absenceDays = request.absenceDays
                        existing.distinction = request.distinction
                        existing.workSanction = request.workSanction
                        existing.conductSanction = request.conductSanction
                        existing.councilDecision = request.councilDecision
                        existing.councilComment = request.councilComment?.trim()?.ifBlank { null }
                        studentPeriodReviewRepository.save(existing)
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            studentPeriodReviewRepository.save(
                                StudentPeriodReview(
                                    schoolId = schoolId,
                                    academicYearId = request.academicYearId,
                                    enrollmentId = request.enrollmentId,
                                    studentId = request.studentId,
                                    classId = request.classId,
                                    period = ReportPeriod(request.periodType, request.periodNumber),
                                    absenceHoursTotal = request.absenceHoursTotal,
                                    absenceHoursUnjustified = request.absenceHoursUnjustified,
                                    lateCount = request.lateCount,
                                    absenceDays = request.absenceDays,
                                    distinction = request.distinction,
                                    workSanction = request.workSanction,
                                    conductSanction = request.conductSanction,
                                    councilDecision = request.councilDecision,
                                    councilComment = request.councilComment?.trim()?.ifBlank { null }
                                )
                            )
                        }
                    )
            )
            .map { toResponse(it) }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<StudentPeriodReviewResponse> {
        ReportingAccess.assertAdmin(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return studentPeriodReviewRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("PERIOD_REVIEW_NOT_FOUND", "Period review not found")))
            .map { toResponse(it) }
    }

    private fun toResponse(review: StudentPeriodReview) = StudentPeriodReviewResponse(
        id = review.id!!,
        schoolId = review.schoolId,
        academicYearId = review.academicYearId,
        enrollmentId = review.enrollmentId,
        studentId = review.studentId,
        classId = review.classId,
        periodType = review.period.type,
        periodNumber = review.period.number,
        absenceHoursTotal = review.absenceHoursTotal,
        absenceHoursUnjustified = review.absenceHoursUnjustified,
        lateCount = review.lateCount,
        absenceDays = review.absenceDays,
        distinction = review.distinction,
        workSanction = review.workSanction,
        conductSanction = review.conductSanction,
        councilDecision = review.councilDecision,
        councilComment = review.councilComment
    )
}
