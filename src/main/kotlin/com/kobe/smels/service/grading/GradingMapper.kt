package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.AppreciationScaleCalcDto
import com.kobe.smels.dto.grading.AssessmentResponse
import com.kobe.smels.dto.grading.EvaluationSessionResponse
import com.kobe.smels.dto.grading.EvaluationSessionSummaryDto
import com.kobe.smels.dto.grading.GradeReportSheetLineDto
import com.kobe.smels.dto.grading.GradeReportSheetResponse
import com.kobe.smels.dto.grading.GradeResponse
import com.kobe.smels.dto.grading.GradeSheetResponse
import com.kobe.smels.dto.grading.GradingSchemeCalcDto
import com.kobe.smels.dto.grading.GradingSchemeResponse
import com.kobe.smels.dto.grading.LocalizedTextDto
import com.kobe.smels.dto.grading.ReportSheetSequenceCellDto
import com.kobe.smels.dto.grading.ReportSheetVisaDto
import com.kobe.smels.dto.grading.ScoreBandCalcDto
import com.kobe.smels.dto.grading.SequenceMarkResponse
import com.kobe.smels.dto.grading.SequenceMarkSummaryDto
import com.kobe.smels.dto.grading.TotalizationRuleDto
import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.grading.ReportSheetSequenceCell
import com.kobe.smels.model.grading.ReportSheetVisa
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.grading.TotalizationRule
import com.kobe.smels.model.shared.LocalizedText

object GradingMapper {
    fun toLocalizedDto(text: LocalizedText): LocalizedTextDto = LocalizedTextDto(text.fr, text.en)
    fun toLocalized(dto: LocalizedTextDto): LocalizedText = LocalizedText(dto.fr?.trim()?.ifBlank { null }, dto.en?.trim()?.ifBlank { null })

    fun toSessionResponse(session: EvaluationSession) = EvaluationSessionResponse(
        id = session.id!!,
        schoolId = session.schoolId,
        academicYearId = session.academicYearId,
        number = session.number,
        termNumber = session.termNumber,
        shortLabel = session.shortLabel,
        name = toLocalizedDto(session.name),
        startDate = session.startDate,
        endDate = session.endDate,
        examDate = session.examDate,
        entryDeadline = session.entryDeadline,
        status = session.status,
        openedAt = session.openedAt,
        closedAt = session.closedAt
    )

    fun toSheetResponse(sheet: GradeSheet) = GradeSheetResponse(
        id = sheet.id!!,
        schoolId = sheet.schoolId,
        academicYearId = sheet.academicYearId,
        classId = sheet.classId,
        classSubjectId = sheet.classSubjectId,
        sessionId = sheet.sessionId,
        sessionNumber = sheet.sessionNumber,
        teacherId = sheet.teacherId,
        status = sheet.status,
        totalization = TotalizationRuleDto(
            formula = sheet.totalization.formula,
            minScore = sheet.totalization.minScore,
            maxScore = sheet.totalization.maxScore,
            capAtMax = sheet.totalization.capAtMax
        ),
        lastTotalizedAt = sheet.lastTotalizedAt
    )

    fun toAssessmentResponse(a: Assessment) = AssessmentResponse(
        id = a.id!!,
        schoolId = a.schoolId,
        sheetId = a.sheetId,
        classId = a.classId,
        classSubjectId = a.classSubjectId,
        sessionId = a.sessionId,
        sessionNumber = a.sessionNumber,
        title = a.title,
        type = a.type,
        maxScore = a.maxScore,
        weight = a.weight,
        date = a.date,
        published = a.published
    )

    fun toGradeResponse(g: Grade) = GradeResponse(
        id = g.id!!,
        schoolId = g.schoolId,
        assessmentId = g.assessmentId,
        studentId = g.studentId,
        enrollmentId = g.enrollmentId,
        classId = g.classId,
        classSubjectId = g.classSubjectId,
        sessionId = g.sessionId,
        value = g.value,
        status = g.status,
        comment = g.comment
    )

    fun toSequenceMarkResponse(m: SequenceMark) = SequenceMarkResponse(
        id = m.id!!,
        schoolId = m.schoolId,
        sheetId = m.sheetId,
        studentId = m.studentId,
        classSubjectId = m.classSubjectId,
        sessionId = m.sessionId,
        sessionNumber = m.sessionNumber,
        termNumber = m.termNumber,
        value = m.value,
        status = m.status,
        source = m.source,
        computedAt = m.computedAt
    )

    fun toSequenceMarkSummary(m: SequenceMark) = SequenceMarkSummaryDto(
        id = m.id!!,
        schoolId = m.schoolId,
        studentId = m.studentId,
        classSubjectId = m.classSubjectId,
        sessionId = m.sessionId,
        sessionNumber = m.sessionNumber,
        termNumber = m.termNumber,
        value = m.value,
        status = m.status
    )

    fun toSchemeResponse(s: GradingScheme) = GradingSchemeResponse(
        id = s.id!!,
        schoolId = s.schoolId,
        code = s.code,
        name = toLocalizedDto(s.name),
        maxScore = s.maxScore,
        passMark = s.passMark,
        decimals = s.decimals,
        reportSheetDeadlineHours = s.reportSheetDeadlineHours,
        reportSheetDeadlineBlocking = s.reportSheetDeadlineBlocking,
        reportSheetMaxRowsPerPage = s.reportSheetMaxRowsPerPage,
        reportSheetAppreciationScaleCode = s.reportSheetAppreciationScaleCode,
        active = s.active
    )

    fun toSchemeCalc(s: GradingScheme) = GradingSchemeCalcDto(
        id = s.id!!,
        schoolId = s.schoolId,
        code = s.code,
        passMark = s.passMark,
        classSuccessThreshold = s.classSuccessThreshold,
        roundTermSubjectAverages = s.roundTermSubjectAverages,
        rankingPolicy = s.rankingPolicy,
        effortSubjectThreshold = s.effortSubjectThreshold,
        appreciationScales = s.appreciationScales.map { scale ->
            AppreciationScaleCalcDto(
                code = scale.code,
                bands = scale.bands.map { band ->
                    ScoreBandCalcDto(
                        code = band.code,
                        labelFr = band.label?.fr,
                        labelEn = band.label?.en,
                        minInclusive = band.minInclusive,
                        maxExclusive = band.maxExclusive
                    )
                }
            )
        },
        overallMentionBands = s.overallMentionBands.map { band ->
            ScoreBandCalcDto(
                code = band.code,
                labelFr = band.label?.fr,
                labelEn = band.label?.en,
                minInclusive = band.minInclusive,
                maxExclusive = band.maxExclusive
            )
        }
    )

    fun toSessionSummary(session: EvaluationSession) = EvaluationSessionSummaryDto(
        id = session.id!!,
        schoolId = session.schoolId,
        academicYearId = session.academicYearId,
        number = session.number,
        termNumber = session.termNumber,
        status = session.status
    )

    fun toReportSheetResponse(sheet: GradeReportSheet, deadlineWarning: Boolean = false) =
        GradeReportSheetResponse(
            id = sheet.id!!,
            schoolId = sheet.schoolId,
            academicYearId = sheet.academicYearId,
            classId = sheet.classId,
            classSubjectId = sheet.classSubjectId,
            termNumber = sheet.termNumber,
            teacherId = sheet.teacherId,
            status = sheet.status,
            teacherLabel = sheet.teacherLabel,
            subjectLabel = toLocalizedDto(sheet.subjectLabel),
            classLabel = sheet.classLabel,
            coefficient = sheet.coefficient,
            headcount = sheet.headcount,
            boysCount = sheet.boysCount,
            girlsCount = sheet.girlsCount,
            sexCountWarning = sheet.sexCountWarning,
            sequence1ExamDate = sheet.sequence1ExamDate,
            sequence2ExamDate = sheet.sequence2ExamDate,
            sequence1Number = sheet.sequence1Number,
            sequence2Number = sheet.sequence2Number,
            appreciationScaleCode = sheet.appreciationScaleCode,
            deadlineWarning = deadlineWarning,
            lines = sheet.lines.map { toReportLineDto(it) },
            submittedAt = sheet.submittedAt,
            visaSupervisor = sheet.visaSupervisor?.let { toVisaDto(it) },
            visaPrincipal = sheet.visaPrincipal?.let { toVisaDto(it) },
            rejectionReason = sheet.rejectionReason
        )

    private fun toVisaDto(v: ReportSheetVisa) = ReportSheetVisaDto(v.userId, v.at)

    private fun toReportLineDto(line: com.kobe.smels.model.grading.GradeReportSheetLine) =
        GradeReportSheetLineDto(
            rollNumber = line.rollNumber,
            studentId = line.studentId,
            studentLabel = line.studentLabel,
            sex = line.sex,
            sequence1 = toCellDto(line.sequence1),
            sequence2 = toCellDto(line.sequence2)
        )

    private fun toCellDto(cell: ReportSheetSequenceCell) = ReportSheetSequenceCellDto(
        mark = cell.mark,
        markStatus = cell.markStatus,
        coefficient = cell.coefficient,
        weightedMark = cell.weightedMark,
        rank = cell.rank,
        appreciation = cell.appreciation,
        appreciationOverride = cell.appreciationOverride,
        displayedAppreciation = cell.displayedAppreciation()
    )

    fun defaultTotalization(): TotalizationRule = TotalizationRule()
}
