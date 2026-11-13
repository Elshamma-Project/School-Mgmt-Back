package com.kobe.smels.repository.grading

import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeCorrectionEvidenceFile
import com.kobe.smels.model.grading.GradeCorrectionRequest
import com.kobe.smels.model.grading.GradeCorrectionStatus
import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.grading.SessionStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface EvaluationSessionRepository : ReactiveMongoRepository<EvaluationSession, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<EvaluationSession>
    fun findBySchoolIdAndAcademicYearIdAndNumber(
        schoolId: String,
        academicYearId: String,
        number: Int
    ): Mono<EvaluationSession>
    fun findBySchoolIdAndAcademicYearId(schoolId: String, academicYearId: String): Flux<EvaluationSession>
    fun findBySchoolIdAndAcademicYearIdAndTermNumber(
        schoolId: String,
        academicYearId: String,
        termNumber: Int
    ): Flux<EvaluationSession>
    fun findBySchoolIdAndAcademicYearIdAndStatus(
        schoolId: String,
        academicYearId: String,
        status: SessionStatus
    ): Flux<EvaluationSession>
}

interface GradeReportSheetRepository : ReactiveMongoRepository<GradeReportSheet, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<GradeReportSheet>
    fun findBySchoolIdAndClassSubjectIdAndTermNumber(
        schoolId: String,
        classSubjectId: String,
        termNumber: Int
    ): Mono<GradeReportSheet>
    fun findBySchoolIdAndTeacherIdAndTermNumber(
        schoolId: String,
        teacherId: String,
        termNumber: Int
    ): Flux<GradeReportSheet>
    fun findBySchoolIdAndClassIdAndTermNumber(
        schoolId: String,
        classId: String,
        termNumber: Int
    ): Flux<GradeReportSheet>
    fun findBySchoolIdAndAcademicYearIdAndTermNumber(
        schoolId: String,
        academicYearId: String,
        termNumber: Int
    ): Flux<GradeReportSheet>
}

interface GradeSheetRepository : ReactiveMongoRepository<GradeSheet, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<GradeSheet>
    fun findBySchoolIdAndClassSubjectIdAndSessionId(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Mono<GradeSheet>
    fun findBySchoolIdAndSessionId(schoolId: String, sessionId: String): Flux<GradeSheet>
    fun findBySchoolIdAndTeacherIdAndSessionId(
        schoolId: String,
        teacherId: String,
        sessionId: String
    ): Flux<GradeSheet>
    fun findBySchoolIdAndClassIdAndSessionId(
        schoolId: String,
        classId: String,
        sessionId: String
    ): Flux<GradeSheet>
}

interface AssessmentRepository : ReactiveMongoRepository<Assessment, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Assessment>
    fun findBySchoolIdAndSheetId(schoolId: String, sheetId: String): Flux<Assessment>
    fun findBySchoolIdAndClassSubjectIdAndSessionId(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Flux<Assessment>
}

interface GradeRepository : ReactiveMongoRepository<Grade, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<Grade>
    fun findBySchoolIdAndAssessmentIdAndStudentId(
        schoolId: String,
        assessmentId: String,
        studentId: String
    ): Mono<Grade>
    fun findBySchoolIdAndAssessmentId(schoolId: String, assessmentId: String): Flux<Grade>
    fun findBySchoolIdAndClassSubjectIdAndSessionIdAndStudentId(
        schoolId: String,
        classSubjectId: String,
        sessionId: String,
        studentId: String
    ): Flux<Grade>
    fun findBySchoolIdAndClassSubjectIdAndSessionId(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Flux<Grade>
    fun deleteBySchoolIdAndAssessmentIdAndStudentId(
        schoolId: String,
        assessmentId: String,
        studentId: String
    ): Mono<Void>
}

interface SequenceMarkRepository : ReactiveMongoRepository<SequenceMark, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<SequenceMark>
    fun findBySchoolIdAndSheetIdAndStudentId(
        schoolId: String,
        sheetId: String,
        studentId: String
    ): Mono<SequenceMark>
    fun findBySchoolIdAndClassSubjectIdAndSessionId(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Flux<SequenceMark>
    fun findBySchoolIdAndClassSubjectId(
        schoolId: String,
        classSubjectId: String
    ): Flux<SequenceMark>
    fun findBySchoolIdAndStudentIdAndAcademicYearIdAndSessionId(
        schoolId: String,
        studentId: String,
        academicYearId: String,
        sessionId: String
    ): Flux<SequenceMark>
    fun findBySchoolIdAndStudentIdAndAcademicYearId(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Flux<SequenceMark>
    fun findBySchoolIdAndStudentId(schoolId: String, studentId: String): Flux<SequenceMark>
}

interface GradeAuditEntryRepository : ReactiveMongoRepository<GradeAuditEntry, String> {
    fun findBySchoolIdAndSessionIdAndAfterSessionClosedTrue(
        schoolId: String,
        sessionId: String,
        pageable: Pageable
    ): Flux<GradeAuditEntry>
    fun findBySchoolId(schoolId: String, pageable: Pageable): Flux<GradeAuditEntry>
    fun countBySchoolId(schoolId: String): Mono<Long>
}

interface GradeCorrectionRequestRepository : ReactiveMongoRepository<GradeCorrectionRequest, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<GradeCorrectionRequest>
    fun findBySchoolIdAndStatusOrderByRequestedAtDesc(
        schoolId: String,
        status: GradeCorrectionStatus
    ): Flux<GradeCorrectionRequest>
    fun findBySchoolIdAndRequestedByOrderByRequestedAtDesc(
        schoolId: String,
        requestedBy: String
    ): Flux<GradeCorrectionRequest>
    fun findBySchoolIdAndSessionIdAndClassSubjectIdAndStatus(
        schoolId: String,
        sessionId: String,
        classSubjectId: String,
        status: GradeCorrectionStatus
    ): Flux<GradeCorrectionRequest>
    fun findBySchoolIdAndSessionIdAndClassSubjectIdAndStudentIdAndAssessmentIdAndStatus(
        schoolId: String,
        sessionId: String,
        classSubjectId: String,
        studentId: String,
        assessmentId: String,
        status: GradeCorrectionStatus
    ): Mono<GradeCorrectionRequest>
}

interface GradeCorrectionEvidenceFileRepository : ReactiveMongoRepository<GradeCorrectionEvidenceFile, String> {
    fun findByIdAndSchoolIdAndRequestId(
        id: String,
        schoolId: String,
        requestId: String
    ): Mono<GradeCorrectionEvidenceFile>
    fun findBySchoolIdAndRequestId(schoolId: String, requestId: String): Flux<GradeCorrectionEvidenceFile>
}

interface GradingSchemeRepository : ReactiveMongoRepository<GradingScheme, String> {
    fun findByIdAndSchoolId(id: String, schoolId: String): Mono<GradingScheme>
    fun findBySchoolIdAndCode(schoolId: String, code: String): Mono<GradingScheme>
    fun findBySchoolIdAndActiveTrue(schoolId: String): Flux<GradingScheme>
    fun findBySchoolId(schoolId: String): Flux<GradingScheme>
}
