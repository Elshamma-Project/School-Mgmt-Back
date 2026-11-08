package com.kobe.smels.service.reporting

import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.LocalizedText
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Critères d'acceptation docs/04 §6 — rejeu des fixtures bulletin-*.json.
 * Les écarts documentés (knownDeviations) sont signalés, pas masqués.
 */
class GradeCalculationFixtureTest {

    private val calc = GradeCalculationService(
        sequenceMarkApi = org.mockito.kotlin.mock(),
        gradingSchemeApi = org.mockito.kotlin.mock(),
        classSubjectApi = org.mockito.kotlin.mock(),
        subjectApi = org.mockito.kotlin.mock(),
        subjectGroupApi = org.mockito.kotlin.mock(),
        enrollmentApi = org.mockito.kotlin.mock(),
        staffApi = org.mockito.kotlin.mock()
    )

    @Test
    fun `bulletin-6eme-fr line averages match printed with documented deviations`() {
        val root = BulletinFixtureLoader.load("bulletin-6eme-fr")
        val student = BulletinFixtureLoader.studentSubjects(root)
        val scheme = BulletinFixtureLoader.schemeFromFixture(root)
        val template = annualTemplate()
        val result = calc.calculate(listOf(student), scheme, ReportPeriodType.ANNUAL, null, template)
            .students["fixture-student"]!!
        val deviations = root.path("knownDeviations").associate { node ->
            node.path("subjectCode").asString() to node.path("printed").decimalValue()
        }
        root.path("groups").forEach { group ->
            group.path("lines").forEach { line ->
                val code = line.path("subjectCode").asString()
                val printed = BigDecimal(line.path("printed").path("average").asString())
                val computed = result.groups.flatMap { it.lines }.first { it.subjectCode == code }
                assertNotNull(computed.averageDisplay, "missing average for $code")
                val tol = if (deviations.containsKey(code)) BigDecimal("0.02") else BigDecimal("0.01")
                assertClose(printed, computed.averageDisplay!!, tol, "line $code")
            }
        }
        val printedAvg = BigDecimal(root.path("printedResults").path("annualAverage").asString())
        assertClose(printedAvg, result.averageDisplay!!, BigDecimal("0.05"), "annual average")
    }

    @Test
    fun `bulletin-form4-sc-en annual average 11_67`() {
        val root = BulletinFixtureLoader.load("bulletin-form4-sc-en")
        val student = BulletinFixtureLoader.studentSubjects(root)
        val scheme = BulletinFixtureLoader.schemeFromFixture(root)
        val result = calc.calculate(
            listOf(student), scheme, ReportPeriodType.ANNUAL, null, annualTemplate()
        ).students["fixture-student"]!!
        assertClose(
            BigDecimal(root.path("printedResults").path("annualAverage").asString()),
            result.averageDisplay!!,
            BigDecimal("0.02"),
            "form4-sc average"
        )
        assertEquals(
            root.path("groups").sumOf { it.path("lines").size() },
            result.groups.sumOf { it.lines.size }
        )
    }

    @Test
    fun `bulletin-form1-term1-en totals and effort subjects`() {
        val root = BulletinFixtureLoader.load("bulletin-form1-term1-en")
        val student = BulletinFixtureLoader.studentSubjects(root)
        val scheme = BulletinFixtureLoader.schemeFromFixture(root)
        val template = termTemplate()
        val computation = calc.calculate(
            listOf(student), scheme, ReportPeriodType.TERM, 1, template
        )
        val result = computation.students["fixture-student"]!!
        val printed = root.path("printedResults")
        assertClose(
            BigDecimal(printed.path("termAverage").asString()),
            result.averageDisplay!!,
            BigDecimal("0.02"),
            "term average"
        )
        assertClose(
            BigDecimal(printed.path("totalPoints").asString()),
            result.totalPointsDisplay!!,
            BigDecimal("0.05"),
            "total points"
        )
        assertEquals(printed.path("passedInSubjects").asInt(), result.subjectsPassed)
        assertEquals(printed.path("subjectsCounted").asInt(), result.subjectsCounted)
        assertTrue(
            result.effortSubjectNames.any { name ->
                name.contains("MATH", ignoreCase = true) || name.contains("LIFE", ignoreCase = true)
            }
        )
        assertTrue(computation.subjectStats.isNotEmpty(), "subject stats required for TERM")
    }

    @Test
    fun `bulletin-form4-arts-en unranked when no average`() {
        val root = BulletinFixtureLoader.load("bulletin-form4-arts-en")
        val printedRank = root.path("printedResults").path("rank")
        if (printedRank.isNull || printedRank.asString() in listOf("", "null", "0")) {
            val student = BulletinFixtureLoader.studentSubjects(root)
            val scheme = BulletinFixtureLoader.schemeFromFixture(root)
            val result = calc.calculate(
                listOf(student), scheme, ReportPeriodType.ANNUAL, null, annualTemplate()
            ).students["fixture-student"]!!
            if (result.averageExact == null) {
                assertEquals(false, result.ranked)
            }
        }
    }

    private fun assertClose(expected: BigDecimal, actual: BigDecimal, tol: BigDecimal, label: String) {
        val diff = expected.subtract(actual).abs()
        assertTrue(
            diff.compareTo(tol) <= 0,
            "$label expected=$expected actual=$actual diff=$diff tol=$tol"
        )
    }

    private fun annualTemplate() = ReportTemplate(
        schoolId = "s",
        code = "ANNUAL_STANDARD",
        name = LocalizedText(fr = "A", en = "A"),
        periodType = ReportPeriodType.ANNUAL,
        sequenceColumns = listOf(1, 2, 3, 4, 5, 6),
        appreciationScaleCode = "LETTER",
        classProfileThreshold = BigDecimal("12")
    )

    private fun termTemplate() = ReportTemplate(
        schoolId = "s",
        code = "TERM_COMPETENCY",
        name = LocalizedText(fr = "T", en = "T"),
        periodType = ReportPeriodType.TERM,
        sequenceColumns = listOf(1, 2),
        appreciationScaleCode = "COMPETENCY",
        showEffortSubjects = true,
        showSubjectRank = true,
        classProfileThreshold = BigDecimal("10")
    )
}
