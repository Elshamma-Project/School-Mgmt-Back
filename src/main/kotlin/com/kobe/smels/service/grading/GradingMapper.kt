package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.AssessmentResponse
import com.kobe.smels.dto.grading.EvaluationSessionResponse
import com.kobe.smels.dto.grading.GradeResponse
import com.kobe.smels.dto.grading.GradeSheetResponse
import com.kobe.smels.dto.grading.GradingSchemeResponse
import com.kobe.smels.dto.grading.LocalizedTextDto
import com.kobe.smels.dto.grading.SequenceMarkResponse
import com.kobe.smels.dto.grading.SequenceMarkSummaryDto
import com.kobe.smels.dto.grading.TotalizationRuleDto
import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradingScheme
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
        active = s.active
    )

    fun defaultTotalization(): TotalizationRule = TotalizationRule()
}
