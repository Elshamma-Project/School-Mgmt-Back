package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.AssessmentResponse
import com.kobe.smels.dto.grading.CreateAssessmentRequest
import com.kobe.smels.dto.grading.UpdateAssessmentRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class AssessmentService(
    private val assessmentRepository: AssessmentRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val academicYearApi: AcademicYearApi
) {
    fun create(currentUser: CurrentUser, request: CreateAssessmentRequest): Mono<AssessmentResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        return gradeSheetRepository.findByIdAndSchoolId(request.sheetId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
            .flatMap { sheet ->
                GradingAccess.assertCanManageSheet(currentUser, sheet.teacherId)
                academicYearApi.assertWritable(schoolId, sheet.academicYearId)
                    .then(evaluationSessionRepository.findByIdAndSchoolId(sheet.sessionId, schoolId))
                    .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Session not found")))
                    .flatMap { session ->
                        if (session.status == SessionStatus.CLOSED &&
                            !currentUser.hasAnyRole(
                                com.kobe.smels.model.user.Role.ADMINISTRATION,
                                com.kobe.smels.model.user.Role.DIRECTOR,
                                com.kobe.smels.model.user.Role.SUPER_ADMIN
                            )
                        ) {
                            Mono.error(BusinessRuleException("SESSION_CLOSED", "Cannot add columns when session is closed"))
                        } else {
                            assessmentRepository.save(
                                Assessment(
                                    schoolId = schoolId,
                                    academicYearId = sheet.academicYearId,
                                    classId = sheet.classId,
                                    classSubjectId = sheet.classSubjectId,
                                    sessionId = sheet.sessionId,
                                    sheetId = sheet.id,
                                    sessionNumber = sheet.sessionNumber,
                                    title = request.title.trim(),
                                    type = request.type,
                                    maxScore = request.maxScore,
                                    weight = request.weight,
                                    date = request.date,
                                    createdByStaffId = GradingAccess.staffProfileId(currentUser),
                                    published = request.published
                                )
                            )
                        }
                    }
            }
            .map { GradingMapper.toAssessmentResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateAssessmentRequest,
        schoolIdOverride: String? = null
    ): Mono<AssessmentResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assessmentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ASSESSMENT_NOT_FOUND", "Assessment not found")))
            .flatMap { assessment ->
                val sheetId = assessment.sheetId
                    ?: return@flatMap Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found"))
                gradeSheetRepository.findByIdAndSchoolId(sheetId, schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
                    .flatMap { sheet ->
                        GradingAccess.assertCanManageSheet(currentUser, sheet.teacherId)
                        academicYearApi.assertWritable(schoolId, sheet.academicYearId)
                            .then(evaluationSessionRepository.findByIdAndSchoolId(sheet.sessionId, schoolId))
                            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Session not found")))
                            .flatMap { session ->
                                if (session.status == SessionStatus.CLOSED &&
                                    !currentUser.hasAnyRole(
                                        com.kobe.smels.model.user.Role.ADMINISTRATION,
                                        com.kobe.smels.model.user.Role.DIRECTOR,
                                        com.kobe.smels.model.user.Role.SUPER_ADMIN
                                    )
                                ) {
                                    Mono.error(
                                        BusinessRuleException(
                                            "SESSION_CLOSED",
                                            "Cannot update columns when session is closed"
                                        )
                                    )
                                } else {
                                    assessment.title = request.title.trim()
                                    assessment.type = request.type
                                    assessment.maxScore = request.maxScore
                                    assessment.weight = request.weight
                                    assessment.date = request.date
                                    assessment.published = request.published
                                    assessmentRepository.save(assessment)
                                }
                            }
                    }
            }
            .map { GradingMapper.toAssessmentResponse(it) }
    }

    fun listBySheet(
        currentUser: CurrentUser,
        sheetId: String,
        schoolIdOverride: String? = null
    ): Flux<AssessmentResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeSheetRepository.findByIdAndSchoolId(sheetId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
            .flatMapMany { sheet ->
                GradingAccess.assertCanManageSheet(currentUser, sheet.teacherId)
                assessmentRepository.findBySchoolIdAndSheetId(schoolId, sheetId)
                    .map { GradingMapper.toAssessmentResponse(it) }
            }
    }
}
