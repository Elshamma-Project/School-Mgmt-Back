package com.kobe.smels.service.reporting

import com.kobe.smels.dto.grading.GradingSchemeCalcDto
import com.kobe.smels.dto.grading.SequenceMarkSummaryDto
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.Language
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.academic.api.SubjectApi
import com.kobe.smels.service.academic.api.SubjectGroupApi
import com.kobe.smels.service.grading.api.GradingSchemeApi
import com.kobe.smels.service.grading.api.SequenceMarkApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.math.BigDecimal

/**
 * Étapes 1–9 (docs/04) pour une classe et une période.
 * Arithmétique déléguée à [GradeCalculationEngine] (sans accès base).
 */
@Service
class GradeCalculationService(
    private val sequenceMarkApi: SequenceMarkApi,
    private val gradingSchemeApi: GradingSchemeApi,
    private val classSubjectApi: ClassSubjectApi,
    private val subjectApi: SubjectApi,
    private val subjectGroupApi: SubjectGroupApi,
    private val enrollmentApi: EnrollmentApi,
    private val staffApi: StaffApi
) {
    fun calculateClass(
        schoolId: String,
        classId: String,
        periodType: ReportPeriodType,
        periodNumber: Int?,
        schemeId: String,
        template: ReportTemplate,
        language: Language
    ): Mono<GradeCalculationEngine.ClassComputation> =
        gradingSchemeApi.getForCalculation(schoolId, schemeId)
            .flatMap { schemeDto ->
                enrollmentApi.findEnrolledByClass(schoolId, classId).collectList()
                    .flatMap { enrollments ->
                        classSubjectApi.findByClass(schoolId, classId).filter { it.active }.collectList()
                            .flatMap { classSubjects ->
                                buildStudentSubjects(
                                    schoolId, classSubjects, enrollments.map { it.studentId }, language
                                ).map { students ->
                                    val columns = sequenceColumnsFor(periodType, periodNumber, template)
                                    calculate(
                                        students = students,
                                        scheme = toSchemeParams(schemeDto),
                                        periodType = periodType,
                                        periodNumber = periodNumber,
                                        template = template,
                                        sequenceColumns = columns
                                    )
                                }
                            }
                    }
            }

    /** Calcul pur (fixtures / tests) — sans I/O. */
    fun calculate(
        students: List<GradeCalculationEngine.StudentSubjects>,
        scheme: GradeCalculationEngine.SchemeParams,
        periodType: ReportPeriodType,
        periodNumber: Int?,
        template: ReportTemplate,
        sequenceColumns: List<Int> = sequenceColumnsFor(periodType, periodNumber, template)
    ): GradeCalculationEngine.ClassComputation =
        GradeCalculationEngine.computeClass(
            students = students,
            scheme = scheme,
            periodType = periodType,
            termNumber = periodNumber,
            appreciationScaleCode = template.appreciationScaleCode,
            classProfileThreshold = template.classProfileThreshold,
            sequenceColumns = sequenceColumns
        )

    fun sequenceColumnsFor(
        periodType: ReportPeriodType,
        periodNumber: Int?,
        template: ReportTemplate
    ): List<Int> =
        when (periodType) {
            ReportPeriodType.TERM -> {
                val t = periodNumber ?: 1
                listOf(t * 2 - 1, t * 2)
            }
            ReportPeriodType.SEQUENCE -> listOf(periodNumber ?: 1)
            ReportPeriodType.ANNUAL -> template.sequenceColumns.ifEmpty { listOf(1, 2, 3, 4, 5, 6) }
        }

    fun toSchemeParams(dto: GradingSchemeCalcDto): GradeCalculationEngine.SchemeParams =
        GradeCalculationEngine.SchemeParams(
            passMark = dto.passMark,
            classSuccessThreshold = dto.classSuccessThreshold,
            roundTermSubjectAverages = dto.roundTermSubjectAverages,
            rankingPolicy = dto.rankingPolicy,
            effortSubjectThreshold = dto.effortSubjectThreshold,
            appreciationScales = dto.appreciationScales.map { scale ->
                GradeCalculationEngine.ScaleInput(
                    code = scale.code,
                    bands = scale.bands.map { band ->
                        GradeCalculationEngine.ScoreBandInput(
                            code = band.code,
                            minInclusive = band.minInclusive,
                            maxExclusive = band.maxExclusive
                        )
                    }
                )
            }.ifEmpty { listOf(GradeCalculationEngine.defaultLetterScale()) },
            overallMentionBands = dto.overallMentionBands.map { band ->
                GradeCalculationEngine.ScoreBandInput(
                    code = band.code,
                    minInclusive = band.minInclusive,
                    maxExclusive = band.maxExclusive
                )
            }
        )

    private fun buildStudentSubjects(
        schoolId: String,
        classSubjects: List<com.kobe.smels.dto.academic.ClassSubjectSummaryDto>,
        studentIds: List<String>,
        language: Language
    ): Mono<List<GradeCalculationEngine.StudentSubjects>> {
        if (classSubjects.isEmpty()) {
            return Mono.just(studentIds.map { GradeCalculationEngine.StudentSubjects(it, emptyList()) })
        }
        return reactor.core.publisher.Flux.fromIterable(classSubjects)
            .concatMap { cs ->
                subjectApi.getSummary(schoolId, cs.subjectId).flatMap { subject ->
                    subjectGroupApi.getSummary(schoolId, cs.groupId).flatMap { group ->
                        resolveTeacherName(schoolId, cs.teacherId).flatMap { teacherName ->
                            sequenceMarkApi.findByClassSubject(schoolId, cs.id).collectList().map { marks ->
                                SubjectBundle(
                                    cs = cs,
                                    subjectCode = subject.code,
                                    subjectName = pick(subject.name.fr, subject.name.en, language),
                                    groupCode = group.code,
                                    groupName = pick(group.name.fr, group.name.en, language),
                                    groupDisplayOrder = group.displayOrder,
                                    teacherName = teacherName.ifBlank { null },
                                    marks = marks
                                )
                            }
                        }
                    }
                }
            }
            .collectList()
            .map { bundles ->
                studentIds.map { studentId ->
                    val subjects = bundles.map { b ->
                        val studentMarks = b.marks.filter { it.studentId == studentId }
                        GradeCalculationEngine.SubjectInput(
                            classSubjectId = b.cs.id,
                            subjectId = b.cs.subjectId,
                            subjectCode = b.subjectCode,
                            subjectName = b.subjectName,
                            groupId = b.cs.groupId,
                            groupCode = b.groupCode,
                            groupName = b.groupName,
                            groupDisplayOrder = b.groupDisplayOrder,
                            coefficient = b.cs.coefficient,
                            teacherName = b.teacherName,
                            countsInAverage = b.cs.countsInAverage,
                            sequences = toSequenceValues(studentMarks)
                        )
                    }
                    GradeCalculationEngine.StudentSubjects(studentId, subjects)
                }
            }
    }

    private fun toSequenceValues(marks: List<SequenceMarkSummaryDto>): List<GradeCalculationEngine.SequenceValue> =
        marks.map {
            GradeCalculationEngine.SequenceValue(
                sessionNumber = it.sessionNumber,
                value = it.value,
                status = it.status
            )
        }.ifEmpty {
            // aucune note : PENDING implicite géré par colonnes du gabarit
            emptyList()
        }

    private fun resolveTeacherName(schoolId: String, teacherId: String?): Mono<String> =
        if (teacherId.isNullOrBlank()) Mono.just("")
        else staffApi.getSummary(schoolId, teacherId)
            .map { it.displayName }
            .onErrorReturn("")

    private fun pick(fr: String?, en: String?, language: Language): String =
        when (language) {
            Language.FR -> fr?.takeIf { it.isNotBlank() } ?: en.orEmpty()
            Language.EN -> en?.takeIf { it.isNotBlank() } ?: fr.orEmpty()
        }

    private data class SubjectBundle(
        val cs: com.kobe.smels.dto.academic.ClassSubjectSummaryDto,
        val subjectCode: String,
        val subjectName: String,
        val groupCode: String,
        val groupName: String,
        val groupDisplayOrder: Int,
        val teacherName: String?,
        val marks: List<SequenceMarkSummaryDto>
    )

    companion object {
        /** Construit une entrée matière depuis une fixture bulletin-*.json (tests). */
        fun subjectFromFixtureLine(
            groupCode: String,
            groupName: String,
            groupDisplayOrder: Int,
            subjectCode: String,
            subjectName: String,
            coefficient: BigDecimal,
            sequenceMarks: List<BigDecimal?>,
            teacherName: String? = null,
            competency: String? = null,
            countsInAverage: Boolean = true,
            startSession: Int = 1
        ): GradeCalculationEngine.SubjectInput {
            val sequences = sequenceMarks.mapIndexed { idx, value ->
                val n = startSession + idx
                if (value == null) {
                    GradeCalculationEngine.SequenceValue(n, null, GradeStatus.PENDING)
                } else {
                    GradeCalculationEngine.SequenceValue(n, value, GradeStatus.SCORED)
                }
            }
            return GradeCalculationEngine.SubjectInput(
                classSubjectId = "cs-$subjectCode",
                subjectId = "sub-$subjectCode",
                subjectCode = subjectCode,
                subjectName = subjectName,
                groupId = "g-$groupCode",
                groupCode = groupCode,
                groupName = groupName,
                groupDisplayOrder = groupDisplayOrder,
                coefficient = coefficient,
                teacherName = teacherName,
                countsInAverage = countsInAverage,
                sequences = sequences,
                competency = competency
            )
        }

        fun requireScheme(schemeId: String?): String =
            schemeId ?: throw NotFoundException("SCHEME_REQUIRED", "Class has no grading scheme")
    }
}
