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
 * Bulletin fictif anonymisé — mêmes chiffres / structure que l’aperçu HTML maquette KOBE.
 */
object SampleReportCardFactory {

    /** Image mock gabarit — `docs/fixtures/mock/sample-student-school-photo.png`. */
    fun loadSampleSchoolPhoto(): ByteArray? =
        SampleReportCardFactory::class.java.getResourceAsStream(
            "/reporting/sample-student-school-photo.png"
        )?.use { it.readBytes() }

    fun build(schoolId: String, template: ReportTemplate, language: Language): ReportCard {
        val term = template.periodType == ReportPeriodType.TERM
        val seq = template.sequenceColumns
        val groups = if (term) termGroups(seq, language) else annualGroups(seq, language)

        return ReportCard(
            id = "sample-${template.code.lowercase()}",
            schoolId = schoolId,
            academicYearId = "year-sample",
            enrollmentId = "enr-sample",
            studentId = "stu-sample",
            classId = "class-sample",
            period = if (term) {
                ReportPeriod(ReportPeriodType.TERM, 1)
            } else {
                ReportPeriod(ReportPeriodType.ANNUAL, null)
            },
            revision = 1,
            status = ReportCardStatus.DRAFT,
            language = language,
            verificationCode = "2027060100123",
            student = StudentSnapshot(
                lastName = "NOM",
                firstName = "Prénoms",
                matricule = "26A0001",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2015, 1, 1),
                placeOfBirth = "Yaoundé",
                repeater = false,
                photoFileId = "sample-school-photo",
            ),
            classInfo = ClassSnapshot(
                className = if (language == Language.FR) "6ème" else "Form 1",
                levelCode = if (language == Language.FR) "6EME" else "FORM1",
                section = if (language == Language.FR) Section.FRANCOPHONE else Section.ANGLOPHONE,
                mainTeacherName = "Mme T1",
                effectif = if (term) 14 else 21,
                rankedCount = if (term) 14 else 21,
                rollNumber = if (term) 6 else 4
            ),
            groups = groups,
            overall = if (term) {
                OverallResult(
                    average = BigDecimal("11.34"),
                    rank = 6,
                    rankedCount = 14,
                    totalCoefficient = BigDecimal("28"),
                    totalPoints = BigDecimal("317.50"),
                    mentionCode = "FAIR",
                    mentionLabel = if (language == Language.FR) "PASSABLE" else "FAIR",
                    subjectsPassed = 10,
                    subjectsCounted = 11,
                    rankStatus = RankStatus.RANKED,
                    effortSubjectNames = if (template.showEffortSubjects) {
                        listOf(if (language == Language.FR) "FRANÇAIS" else "FRENCH")
                    } else {
                        emptyList()
                    }
                )
            } else {
                OverallResult(
                    average = BigDecimal("10.80"),
                    rank = 4,
                    rankedCount = 21,
                    totalCoefficient = groups.fold(BigDecimal.ZERO) { a, g -> a + g.totalCoefficient },
                    totalPoints = groups.mapNotNull { it.totalPoints }.fold(BigDecimal.ZERO) { a, b -> a + b },
                    mentionCode = "PASSABLE",
                    mentionLabel = if (language == Language.FR) "PASSABLE" else "FAIR",
                    subjectsPassed = null,
                    subjectsCounted = groups.sumOf { it.lines.size },
                    rankStatus = RankStatus.RANKED,
                    effortSubjectNames = emptyList()
                )
            },
            termRecaps = if (template.showTermRecap) {
                listOf(
                    TermRecap(1, BigDecimal("10.64"), 5, 21),
                    TermRecap(2, BigDecimal("10.78"), 4, 21),
                    TermRecap(3, BigDecimal("10.99"), 4, 21)
                )
            } else {
                emptyList()
            },
            classProfile = if (template.showClassProfile) {
                if (term) {
                    ClassProfile(
                        threshold = template.classProfileThreshold ?: BigDecimal("10"),
                        countAtOrAboveThreshold = 10,
                        classAverage = BigDecimal("11.92"),
                        successRate = BigDecimal("71.43"),
                        highestAverage = BigDecimal("15.10"),
                        lowestAverage = BigDecimal("7.21")
                    )
                } else {
                    ClassProfile(
                        threshold = template.classProfileThreshold ?: BigDecimal("12"),
                        countAtOrAboveThreshold = 6,
                        classAverage = BigDecimal("10.84"),
                        successRate = BigDecimal("28.57"),
                        highestAverage = BigDecimal("14.62"),
                        lowestAverage = BigDecimal("6.38")
                    )
                }
            } else {
                null
            },
            review = ReviewSnapshot(
                absenceHoursTotal = 4,
                absenceHoursUnjustified = 1,
                lateCount = 2,
                distinction = DistinctionLevel.NONE,
                workSanction = SanctionLevel.NONE,
                conductSanction = SanctionLevel.NONE
            ),
            signature = SignatureBlock(
                place = "Yaoundé",
                date = LocalDate.of(2027, 6, 30),
                signatoryTitle = if (language == Language.FR) "LE CHEF D'ÉTABLISSEMENT" else "THE PRINCIPAL",
                signatoryName = null
            ),
            templateCode = template.code,
            calculationVersion = ReportTemplateService.CALCULATION_VERSION,
            generatedAt = if (term) {
                Instant.parse("2026-12-17T08:40:00Z")
            } else {
                Instant.parse("2027-07-01T09:15:00Z")
            }
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
        fun line(
            id: String,
            code: String,
            nameFr: String,
            nameEn: String,
            teacher: String,
            values: Array<String>,
            coef: String,
            avg: String,
            total: String,
            app: String,
            order: Int
        ) = ReportLine(
            classSubjectId = "cs-$id",
            subjectId = "subj-$id",
            subjectCode = code,
            subjectName = if (language == Language.FR) nameFr else nameEn,
            teacherName = teacher,
            marks = marks(*values),
            average = BigDecimal(avg),
            coefficient = BigDecimal(coef),
            total = BigDecimal(total),
            appreciationCode = app,
            appreciationLabel = app,
            displayOrder = order
        )

        val langLines = listOf(
            line("ang", "ANG", "Anglais", "English", "Mme T1", arrayOf("12", "11.5", "13", "10.5", "12.5", "11"), "3", "11.75", "35.25", "B", 1),
            line("ee", "EE", "Expression écrite", "Written expression", "Mme T2", arrayOf("14", "13.5", "12", "15", "13", "14.5"), "2", "13.67", "27.34", "B+", 2),
            line("eo", "EO", "Expression orale", "Oral expression", "Mme T2", arrayOf("13", "12", "11.5", "13.5", "12.5", "13"), "2", "12.58", "25.16", "B", 3),
            line("et", "ET", "Étude de texte", "Text study", "Mme T2", arrayOf("11", "10", "9.5", "12", "11", "10.5"), "2", "10.67", "21.34", "C", 4),
            line("ln", "LN", "Langues nationales", "National languages", "M. T4", arrayOf("8.5", "9", "10", "8", "9.5", "10"), "2", "9.17", "18.34", "C", 5),
            line("cit", "CIT", "Citoyenneté", "Citizenship", "M. T5", arrayOf("15", "14", "13.5", "14.5", "15", "14"), "2", "14.33", "28.66", "A", 6),
        )
        val sciLines = listOf(
            line("math", "MATH", "Mathématiques", "Mathematics", "Mme T1", arrayOf("9", "8.5", "10", "11", "9.5", "10.5"), "4", "9.75", "39.00", "C", 1),
            line("svt", "SVT", "SVTEEHB", "SVTEEHB", "M. T6", arrayOf("11", "12", "10.5", "11.5", "12", "11"), "3", "11.33", "33.99", "B", 2),
            line("info", "INFO", "Informatique", "ICT", "M. T7", arrayOf("13", "12.5", "14", "13", "12", "13.5"), "2", "13.00", "26.00", "B+", 3),
            line("geo", "GEO", "Géographie", "Geography", "Mme T8", arrayOf("10", "9.5", "11", "10.5", "9", "10"), "3", "10.00", "30.00", "C", 4),
            line("hist", "HIST", "Histoire", "History", "Mme T8", arrayOf("7", "8.5", "9", "8", "7.5", "8.5"), "2", "8.08", "16.16", "D", 5),
            line("eps", "EPS", "EPS", "PE", "M. T9", arrayOf("14", "15", "13.5", "14.5", "15", "14"), "2", "14.33", "28.66", "A", 6),
        )

        return listOf(
            ReportGroup(
                groupId = "g-lang",
                code = "LANG",
                name = if (language == Language.FR) "LANGUES ET LITTÉRATURES" else "LANGUAGE AND LITERATURE",
                displayOrder = 1,
                lines = langLines,
                totalCoefficient = BigDecimal("13"),
                totalPoints = BigDecimal("156.09"),
                average = BigDecimal("11.44")
            ),
            ReportGroup(
                groupId = "g-sci",
                code = "SCI",
                name = if (language == Language.FR) "SCIENCES ET TECHNOLOGIE" else "SCIENCE AND TECHNOLOGY",
                displayOrder = 2,
                lines = sciLines,
                totalCoefficient = BigDecimal("16"),
                totalPoints = BigDecimal("173.81"),
                average = BigDecimal("10.12")
            )
        )
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
            subjectName = if (language == Language.FR) "Mathématiques" else "Mathematics",
            teacherName = "Mme T1",
            marks = marks("11.00", "13.50"),
            average = BigDecimal("12.25"),
            coefficient = BigDecimal("4"),
            total = BigDecimal("49.00"),
            appreciationCode = "B",
            appreciationLabel = "B",
            competency = if (language == Language.FR) {
                "Résoudre des problèmes du 1er degré"
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
            teacherName = "Mr T3",
            marks = marks("14.00", "12.50"),
            average = BigDecimal("13.25"),
            coefficient = BigDecimal("3"),
            total = BigDecimal("39.75"),
            appreciationCode = "B+",
            appreciationLabel = "B+",
            competency = if (language == Language.FR) {
                "Comprendre un texte narratif"
            } else {
                "Understand a narrative text"
            },
            subjectRank = 3,
            subjectClassAverage = BigDecimal("11.20"),
            subjectPassedCount = 22,
            displayOrder = 2
        )
        return listOf(
            ReportGroup(
                groupId = "g-sci",
                code = "SCI",
                name = if (language == Language.FR) "SCIENCES" else "SCIENCES",
                displayOrder = 1,
                lines = listOf(lineMath),
                totalCoefficient = BigDecimal("4"),
                totalPoints = BigDecimal("49.00"),
                average = BigDecimal("12.25")
            ),
            ReportGroup(
                groupId = "g-lang",
                code = "LANG",
                name = if (language == Language.FR) "LANGUES" else "LANGUAGES",
                displayOrder = 2,
                lines = listOf(lineEng),
                totalCoefficient = BigDecimal("3"),
                totalPoints = BigDecimal("39.75"),
                average = BigDecimal("13.25")
            )
        )
    }
}
