package com.kobe.smels.service.reporting

import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.reporting.ClassProfile
import com.kobe.smels.model.reporting.ClassSnapshot
import com.kobe.smels.model.reporting.OverallResult
import com.kobe.smels.model.reporting.RankStatus
import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportGroup
import com.kobe.smels.model.reporting.ReportLine
import com.kobe.smels.model.reporting.ReportMark
import com.kobe.smels.model.reporting.ReportPeriod
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.reporting.ReviewSnapshot
import com.kobe.smels.model.reporting.SanctionLevel
import com.kobe.smels.model.reporting.SignatureBlock
import com.kobe.smels.model.reporting.StudentSnapshot
import com.kobe.smels.model.reporting.TermRecap
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Bulletin fictif anonymisé pour prévisualiser un [ReportTemplate] (maquette Z1–Z10).
 * Aucune donnée réelle d’élève.
 */
object SampleReportCardFactory {

    fun build(schoolId: String, template: ReportTemplate, language: Language): ReportCard {
        val term = template.periodType == ReportPeriodType.TERM
        val seq = template.sequenceColumns
        val groups = if (term) termGroups(seq, language) else annualGroups(seq, language)
        val totalCoef = groups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalCoefficient }
        val totalPoints = groups.mapNotNull { it.totalPoints }.fold(BigDecimal.ZERO) { a, b -> a + b }
        val average = if (totalCoef.compareTo(BigDecimal.ZERO) == 0) null
        else totalPoints.divide(totalCoef, 2, java.math.RoundingMode.HALF_UP)

        return ReportCard(
            id = "sample-${template.code.lowercase()}",
            schoolId = schoolId,
            academicYearId = "year-sample",
            enrollmentId = "enr-sample",
            studentId = "stu-sample",
            classId = "class-sample",
            period = if (term) ReportPeriod(ReportPeriodType.TERM, 1) else ReportPeriod(ReportPeriodType.ANNUAL, null),
            revision = 1,
            status = ReportCardStatus.DRAFT,
            language = language,
            verificationCode = "2503698410123",
            student = StudentSnapshot(
                lastName = "NOM",
                firstName = "PRENOMS",
                matricule = "250369841",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2012, 3, 15),
                placeOfBirth = "YAOUNDE",
                repeater = false
            ),
            classInfo = ClassSnapshot(
                className = if (language == Language.FR) "6EME A" else "FORM 1",
                levelCode = if (language == Language.FR) "6EME" else "FORM1",
                section = if (language == Language.FR) Section.FRANCOPHONE else Section.ANGLOPHONE,
                mainTeacherName = "ENSEIGNANT DEMO",
                effectif = 28,
                rankedCount = 26,
                rollNumber = 12
            ),
            groups = groups,
            overall = OverallResult(
                average = average,
                rank = 8,
                rankedCount = 26,
                totalCoefficient = totalCoef,
                totalPoints = totalPoints,
                mentionCode = "PASSABLE",
                mentionLabel = if (language == Language.FR) "TRAVAIL PASSABLE" else "FAIR",
                subjectsPassed = if (term) 12 else null,
                subjectsCounted = groups.sumOf { it.lines.size },
                rankStatus = RankStatus.RANKED,
                effortSubjectNames = if (template.showEffortSubjects) {
                    listOf(if (language == Language.FR) "MATHEMATIQUES" else "MATHEMATICS")
                } else emptyList()
            ),
            termRecaps = if (template.showTermRecap) {
                listOf(
                    TermRecap(1, BigDecimal("11.25"), 10, 26),
                    TermRecap(2, BigDecimal("12.40"), 7, 26),
                    TermRecap(3, BigDecimal("11.80"), 8, 26)
                )
            } else emptyList(),
            classProfile = if (template.showClassProfile) {
                ClassProfile(
                    threshold = template.classProfileThreshold ?: BigDecimal("12"),
                    countAtOrAboveThreshold = 11,
                    classAverage = BigDecimal("11.42"),
                    successRate = BigDecimal("42.31"),
                    highestAverage = BigDecimal("16.80"),
                    lowestAverage = BigDecimal("5.10")
                )
            } else null,
            review = ReviewSnapshot(
                absenceHoursTotal = 4,
                absenceHoursUnjustified = 1,
                lateCount = 2,
                distinction = DistinctionLevel.NONE,
                workSanction = SanctionLevel.NONE,
                conductSanction = SanctionLevel.NONE
            ),
            signature = SignatureBlock(
                place = "YAOUNDE",
                date = LocalDate.of(2026, 5, 26),
                signatoryTitle = if (language == Language.FR) "LE PRINCIPAL" else "THE PRINCIPAL",
                signatoryName = "CHEF D'ETABLISSEMENT"
            ),
            templateCode = template.code,
            calculationVersion = ReportTemplateService.CALCULATION_VERSION,
            generatedAt = Instant.parse("2026-05-26T10:00:00Z")
        )
    }

    private fun annualGroups(seq: List<Int>, language: Language): List<ReportGroup> {
        fun marks(vararg values: String) = seq.mapIndexed { i, n ->
            val raw = values.getOrNull(i)
            ReportMark(
                sessionNumber = n,
                label = "DEV$n",
                value = raw?.let { BigDecimal(it) },
                status = if (raw == null) GradeStatus.PENDING else GradeStatus.SCORED
            )
        }
        val lineMath = ReportLine(
            classSubjectId = "cs-math",
            subjectId = "subj-math",
            subjectCode = "MATH",
            subjectName = if (language == Language.FR) "Mathematiques" else "Mathematics",
            teacherName = "T1",
            marks = marks("12.50", "11.00", "13.25", "10.50", "14.00", "12.00"),
            average = BigDecimal("12.21"),
            coefficient = BigDecimal("4"),
            total = BigDecimal("48.84"),
            appreciationCode = "B",
            appreciationLabel = "B",
            displayOrder = 1
        )
        val lineFr = ReportLine(
            classSubjectId = "cs-fr",
            subjectId = "subj-fr",
            subjectCode = "FR",
            subjectName = if (language == Language.FR) "Francais" else "French",
            teacherName = "T2",
            marks = marks("14.00", "13.50", "12.00", "15.00", "13.00", "14.50"),
            average = BigDecimal("13.67"),
            coefficient = BigDecimal("3"),
            total = BigDecimal("41.01"),
            appreciationCode = "B+",
            appreciationLabel = "B+",
            displayOrder = 2
        )
        val g1 = ReportGroup(
            groupId = "g-lang",
            code = "LANG",
            name = if (language == Language.FR) "TOTAL LANGUES ET LITTERATURES" else "TOTAL LANGUAGES AND LITERATURE",
            displayOrder = 1,
            lines = listOf(lineFr),
            totalCoefficient = BigDecimal("3"),
            totalPoints = BigDecimal("41.01"),
            average = BigDecimal("13.67")
        )
        val g2 = ReportGroup(
            groupId = "g-sci",
            code = "SCI",
            name = if (language == Language.FR) "TOTAL SCIENCES" else "TOTAL SCIENCES",
            displayOrder = 2,
            lines = listOf(lineMath),
            totalCoefficient = BigDecimal("4"),
            totalPoints = BigDecimal("48.84"),
            average = BigDecimal("12.21")
        )
        return listOf(g1, g2)
    }

    private fun termGroups(seq: List<Int>, language: Language): List<ReportGroup> {
        fun marks(vararg values: String) = seq.mapIndexed { i, n ->
            val raw = values.getOrNull(i)
            ReportMark(
                sessionNumber = n,
                label = "Eval${i + 1}",
                value = raw?.let { BigDecimal(it) },
                status = if (raw == null) GradeStatus.PENDING else GradeStatus.SCORED
            )
        }
        val lineMath = ReportLine(
            classSubjectId = "cs-math",
            subjectId = "subj-math",
            subjectCode = "MATH",
            subjectName = if (language == Language.FR) "Mathematiques" else "Mathematics",
            teacherName = "T1",
            marks = marks("11.00", "13.50"),
            average = BigDecimal("12.25"),
            coefficient = BigDecimal("4"),
            total = BigDecimal("49.00"),
            appreciationCode = "A",
            appreciationLabel = "A",
            competency = if (language == Language.FR) {
                "Resoudre des problemes du premier degre"
            } else {
                "Solve first-degree problems"
            },
            subjectRank = 6,
            subjectClassAverage = BigDecimal("10.80"),
            subjectPassedCount = 18,
            displayOrder = 1
        )
        val lineEng = ReportLine(
            classSubjectId = "cs-eng",
            subjectId = "subj-eng",
            subjectCode = "ENG",
            subjectName = "English",
            teacherName = "T3",
            marks = marks("14.00", "12.50"),
            average = BigDecimal("13.25"),
            coefficient = BigDecimal("3"),
            total = BigDecimal("39.75"),
            appreciationCode = "A+",
            appreciationLabel = "A+",
            competency = if (language == Language.FR) {
                "Comprendre un texte narratif simple"
            } else {
                "Understand a simple narrative text"
            },
            subjectRank = 3,
            subjectClassAverage = BigDecimal("11.20"),
            subjectPassedCount = 22,
            displayOrder = 2
        )
        return listOf(
            ReportGroup(
                groupId = "g-core",
                code = "CORE",
                name = if (language == Language.FR) "TOTAL MATIERES PRINCIPALES" else "TOTAL CORE SUBJECTS",
                displayOrder = 1,
                lines = listOf(lineMath, lineEng),
                totalCoefficient = BigDecimal("7"),
                totalPoints = BigDecimal("88.75"),
                average = BigDecimal("12.68")
            )
        )
    }
}
