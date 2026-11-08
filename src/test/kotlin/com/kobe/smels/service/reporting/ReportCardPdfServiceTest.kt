package com.kobe.smels.service.reporting

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
import com.kobe.smels.model.reporting.SignatureBlock
import com.kobe.smels.model.reporting.StudentSnapshot
import com.kobe.smels.model.reporting.TermRecap
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportCardPdfServiceTest {

    private val pdf = ReportCardPdfService()

    @Test
    fun `annual FR fixture style fits on exactly one A4 page`() {
        val card = sampleCard(Language.FR, ReportPeriodType.ANNUAL, null, manySubjects = true)
        val template = annualTemplate()
        val bytes = pdf.render(card, template, "2025-2026")
        assertEquals(1, pdf.pageCount(bytes))
    }

    @Test
    fun `annual EN fixture style fits on exactly one A4 page`() {
        val card = sampleCard(Language.EN, ReportPeriodType.ANNUAL, null, manySubjects = true)
        val bytes = pdf.render(card, annualTemplate(), "2025-2026")
        assertEquals(1, pdf.pageCount(bytes))
    }

    @Test
    fun `term competency EN fits on one page with legend`() {
        val card = sampleCard(Language.EN, ReportPeriodType.TERM, 1, manySubjects = true, term = true)
        val template = termTemplate()
        val bytes = pdf.render(card, template, "2025-2026")
        assertEquals(1, pdf.pageCount(bytes))
    }

    private fun annualTemplate() = ReportTemplate(
        schoolId = "school-1",
        code = "ANNUAL_STANDARD",
        name = LocalizedText(fr = "Annuel", en = "Annual"),
        periodType = ReportPeriodType.ANNUAL,
        sequenceColumns = listOf(1, 2, 3, 4, 5, 6),
        appreciationScaleCode = "LETTER",
        showTermRecap = true,
        showClassProfile = true,
        minFontSizePt = 6.0,
        maxFontSizePt = 8.5
    )

    private fun termTemplate() = ReportTemplate(
        schoolId = "school-1",
        code = "TERM_COMPETENCY",
        name = LocalizedText(fr = "Trimestre", en = "Term"),
        periodType = ReportPeriodType.TERM,
        sequenceColumns = listOf(1, 2),
        appreciationScaleCode = "COMPETENCY",
        showCompetency = true,
        showSubjectRank = true,
        showEffortSubjects = true,
        showTotalPointsOverMax = true,
        showTermRecap = false,
        legend = LocalizedText(
            fr = "NA = non acquis ; ECA = en cours d'acquisition ; A = acquis",
            en = "NA = not acquired ; ECA = being acquired ; A = acquired"
        ),
        minFontSizePt = 6.0,
        maxFontSizePt = 8.5
    )

    private fun sampleCard(
        language: Language,
        periodType: ReportPeriodType,
        periodNumber: Int?,
        manySubjects: Boolean,
        term: Boolean = false
    ): ReportCard {
        val seqCols = if (term) listOf(1, 2) else listOf(1, 2, 3, 4, 5, 6)
        val subjectNames = if (manySubjects) {
            listOf(
                "ANGLAIS", "EXPRESSION ECRITE", "EXPRESSION ORALE", "FRANCAIS",
                "HISTOIRE", "GEOGRAPHIE", "MATHEMATIQUES", "PHYSIQUE",
                "SVT", "EPS", "ARTS", "INFORMATIQUE",
                "ECM", "LCN", "APTITUDE DE VIE", "TRAVAIL MANUEL"
            )
        } else listOf("MATH", "ENG")
        val lines = subjectNames.mapIndexed { idx, name ->
            ReportLine(
                classSubjectId = "cs-$idx",
                subjectId = "s-$idx",
                subjectCode = "C$idx",
                subjectName = name,
                teacherName = "T${idx % 5}",
                marks = seqCols.map { n ->
                    ReportMark(n, if (term) "Eval$n" else "DEV$n", BigDecimal("12.50"), GradeStatus.SCORED)
                },
                average = BigDecimal("12.50"),
                coefficient = BigDecimal("2"),
                total = BigDecimal("25.00"),
                appreciationCode = if (term) "ECA" else "B",
                appreciationLabel = if (term) "E.C.A" else "B",
                competency = if (term) "Competency sample text for $name" else null,
                subjectRank = if (term) idx + 1 else null,
                subjectClassAverage = if (term) BigDecimal("11.20") else null,
                subjectPassedCount = if (term) 10 else null
            )
        }
        val group = ReportGroup(
            groupId = "g1",
            code = "ALL",
            name = if (language == Language.FR) "ENSEMBLE" else "ALL SUBJECTS",
            displayOrder = 1,
            lines = lines,
            totalCoefficient = BigDecimal(lines.size * 2),
            totalPoints = BigDecimal(lines.size * 25),
            average = BigDecimal("12.50")
        )
        return ReportCard(
            schoolId = "school-1",
            academicYearId = "year-1",
            enrollmentId = "enr-1",
            studentId = "stu-1",
            classId = "class-1",
            period = ReportPeriod(periodType, periodNumber),
            revision = 1,
            status = ReportCardStatus.DRAFT,
            language = language,
            verificationCode = "1234567890123",
            student = StudentSnapshot(
                lastName = "TEST",
                firstName = "Eleve",
                matricule = "MAT-001",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2012, 5, 1),
                placeOfBirth = "Yaounde",
                repeater = false,
                photoFileId = if (language == Language.EN) "photo-1" else null
            ),
            classInfo = ClassSnapshot(
                className = if (language == Language.FR) "6EME A" else "FORM 1",
                levelCode = "6EME",
                section = if (language == Language.FR) Section.FRANCOPHONE else Section.ANGLOPHONE,
                mainTeacherName = "M. Principal",
                effectif = 30,
                rankedCount = 28,
                rollNumber = 12
            ),
            groups = listOf(group),
            overall = OverallResult(
                average = BigDecimal("12.50"),
                rank = 10,
                rankedCount = 28,
                totalCoefficient = BigDecimal(lines.size * 2),
                totalPoints = BigDecimal(lines.size * 25),
                mentionCode = "FAIR",
                mentionLabel = if (language == Language.FR) "PASSABLE" else "FAIR",
                subjectsPassed = 12,
                subjectsCounted = lines.size,
                rankStatus = RankStatus.RANKED,
                effortSubjectNames = if (term) listOf("MATHEMATIQUES") else emptyList()
            ),
            termRecaps = if (!term) listOf(
                TermRecap(1, BigDecimal("12.10"), 11, 28),
                TermRecap(2, BigDecimal("12.40"), 10, 28),
                TermRecap(3, BigDecimal("12.90"), 9, 28)
            ) else emptyList(),
            classProfile = ClassProfile(
                threshold = if (term) BigDecimal("10") else BigDecimal("12"),
                countAtOrAboveThreshold = 15,
                classAverage = BigDecimal("11.80"),
                successRate = BigDecimal("53.57"),
                highestAverage = BigDecimal("16.20"),
                lowestAverage = BigDecimal("7.50")
            ),
            review = ReviewSnapshot(),
            signature = SignatureBlock(
                place = "YAOUNDE",
                date = LocalDate.of(2026, 5, 26),
                signatoryTitle = "LE PRINCIPAL",
                signatoryName = "NOM"
            ),
            templateCode = if (term) "TERM_COMPETENCY" else "ANNUAL_STANDARD",
            calculationVersion = "1.0",
            generatedAt = Instant.parse("2026-05-26T10:00:00Z")
        )
    }
}
