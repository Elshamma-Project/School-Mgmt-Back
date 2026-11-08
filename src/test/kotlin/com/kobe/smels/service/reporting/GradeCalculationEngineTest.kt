package com.kobe.smels.service.reporting

import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.reporting.ReportPeriodType
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GradeCalculationEngineTest {

    private val scheme = GradeCalculationEngine.SchemeParams(
        roundTermSubjectAverages = true,
        appreciationScales = listOf(GradeCalculationEngine.defaultLetterScale()),
        passMark = BigDecimal("10"),
        classSuccessThreshold = BigDecimal("12"),
        effortSubjectThreshold = BigDecimal("8")
    )

    @Test
    fun `annual line average with term rounding matches fixture style`() {
        // French: T1=(15.25), T2=(15.25), T3=(12.75+12)/2=12.375→12.38
        val subject = GradeCalculationEngine.SubjectInput(
            classSubjectId = "cs1",
            subjectId = "s1",
            subjectCode = "FR",
            subjectName = "French",
            groupId = "g1",
            groupCode = "LANG",
            groupName = "LANG",
            groupDisplayOrder = 1,
            coefficient = BigDecimal("5"),
            teacherName = "T",
            countsInAverage = true,
            sequences = listOf(
                seq(1, "15.25"), seq(2, "15.25"),
                seq(3, "15.25"), seq(4, "15.25"),
                seq(5, "12.75"), seq(6, "12.00")
            )
        )
        // simplify: use marks as in docs example for French FORM4
        val subject2 = subject.copy(
            sequences = listOf(
                seq(1, "15.25"), seq(2, "15.25"),
                seq(3, "15.25"), seq(4, "15.25"),
                seq(5, "12.75"), seq(6, "12.00")
            )
        )
        val student = GradeCalculationEngine.StudentSubjects("stu", listOf(subject2))
        val result = GradeCalculationEngine.computeStudent(
            student, scheme, ReportPeriodType.ANNUAL, null,
            GradeCalculationEngine.defaultLetterScale(),
            listOf(1, 2, 3, 4, 5, 6)
        )
        val line = result.groups.first().lines.first()
        assertNotNull(line.averageDisplay)
        // (15.25+15.25+12.38)/3 = 14.2933… → display 14.29
        assertEquals(0, line.averageDisplay!!.compareTo(BigDecimal("14.29")))
        assertEquals(0, line.totalDisplay!!.compareTo(BigDecimal("71.47")))
    }

    @Test
    fun `PENDING sequences ignored never zero`() {
        val subject = GradeCalculationEngine.SubjectInput(
            classSubjectId = "cs1",
            subjectId = "s1",
            subjectCode = "M",
            subjectName = "Math",
            groupId = "g1",
            groupCode = "SCI",
            groupName = "SCI",
            groupDisplayOrder = 1,
            coefficient = BigDecimal.ONE,
            teacherName = null,
            countsInAverage = true,
            sequences = listOf(
                seq(1, "10"), seq(2, "12"),
                GradeCalculationEngine.SequenceValue(3, null, GradeStatus.PENDING),
                GradeCalculationEngine.SequenceValue(4, null, GradeStatus.PENDING),
                seq(5, "8"),
                GradeCalculationEngine.SequenceValue(6, null, GradeStatus.PENDING)
            )
        )
        val result = GradeCalculationEngine.computeStudent(
            GradeCalculationEngine.StudentSubjects("stu", listOf(subject)),
            scheme, ReportPeriodType.ANNUAL, null,
            GradeCalculationEngine.defaultLetterScale(),
            listOf(1, 2, 3, 4, 5, 6)
        )
        val line = result.groups.first().lines.first()
        // T1=(10+12)/2=11, T2=none, T3=8 → (11+8)/2=9.5
        assertEquals(0, line.averageDisplay!!.compareTo(BigDecimal("9.50")))
    }

    @Test
    fun `competition ranks 1 2 2 4`() {
        val ranks = GradeCalculationEngine.competitionRanks(
            listOf(
                "a" to BigDecimal("15"),
                "b" to BigDecimal("14"),
                "c" to BigDecimal("14"),
                "d" to BigDecimal("10")
            )
        )
        assertEquals(1, ranks["a"])
        assertEquals(2, ranks["b"])
        assertEquals(2, ranks["c"])
        assertEquals(4, ranks["d"])
    }

    @Test
    fun `class profile success rate`() {
        val students = listOf(
            studentWithAvg("s1", "14"),
            studentWithAvg("s2", "13"),
            studentWithAvg("s3", "11"),
            studentWithAvg("s4", "8.81")
        )
        // 4 students need proper subjects - use computeClass with simple data
        val inputs = listOf(
            singleSubjectStudent("s1", "14"),
            singleSubjectStudent("s2", "13"),
            singleSubjectStudent("s3", "11"),
            singleSubjectStudent("s4", "8.81")
        )
        val computation = GradeCalculationEngine.computeClass(
            inputs, scheme, ReportPeriodType.ANNUAL,
            appreciationScaleCode = "LETTER",
            classProfileThreshold = BigDecimal("12")
        )
        assertEquals(4, computation.rankedCount)
        assertEquals(2, computation.classProfile!!.countAtOrAboveThreshold)
        assertEquals(0, computation.classProfile!!.successRate!!.compareTo(BigDecimal("50.00")))
    }

    @Test
    fun `appreciation LETTER bands`() {
        val scale = GradeCalculationEngine.defaultLetterScale()
        assertEquals("C", GradeCalculationEngine.findBand(BigDecimal("11.99"), scale.bands)!!.code)
        assertEquals("B", GradeCalculationEngine.findBand(BigDecimal("12.00"), scale.bands)!!.code)
        assertEquals("A", GradeCalculationEngine.findBand(BigDecimal("16.00"), scale.bands)!!.code)
    }

    private fun seq(n: Int, v: String) =
        GradeCalculationEngine.SequenceValue(n, BigDecimal(v), GradeStatus.SCORED)

    private fun singleSubjectStudent(id: String, avgAsAllDev: String) =
        GradeCalculationEngine.StudentSubjects(
            id,
            listOf(
                GradeCalculationEngine.SubjectInput(
                    classSubjectId = "cs-$id",
                    subjectId = "sub",
                    subjectCode = "X",
                    subjectName = "X",
                    groupId = "g",
                    groupCode = "G",
                    groupName = "G",
                    groupDisplayOrder = 1,
                    coefficient = BigDecimal.ONE,
                    teacherName = null,
                    countsInAverage = true,
                    sequences = (1..6).map { seq(it, avgAsAllDev) }
                )
            )
        )

    private fun studentWithAvg(id: String, avg: String) = id to avg
}
