package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.TotalizationFormula
import com.kobe.smels.model.grading.TotalizationRule
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SequenceMarkCalculatorTest {
    @Test
    fun `PENDING grades are ignored and never become zero`() {
        val assessment = assessment("a1", AssessmentType.TEST)
        val columns = listOf(
            SequenceMarkCalculator.InputColumn(
                assessment,
                Grade(
                    schoolId = "school-1",
                    academicYearId = "y",
                    classId = "c",
                    classSubjectId = "cs",
                    sessionId = "s",
                    assessmentId = "a1",
                    studentId = "stu",
                    enrollmentId = "e",
                    status = GradeStatus.PENDING,
                    value = null
                )
            )
        )
        val result = SequenceMarkCalculator.totalize(TotalizationRule(), columns)
        assertEquals(GradeStatus.PENDING, result.status)
        assertNull(result.value)
    }

    @Test
    fun `average plus bonus minus malus with cap at 20`() {
        val test = assessment("a1", AssessmentType.TEST, max = "20")
        val bonus = assessment("a2", AssessmentType.BONUS, max = "20")
        val malus = assessment("a3", AssessmentType.MALUS, max = "20")
        val columns = listOf(
            col(test, "12.00"),
            col(bonus, "2.00"),
            col(malus, "1.00")
        )
        val result = SequenceMarkCalculator.totalize(TotalizationRule(), columns)
        assertEquals(GradeStatus.SCORED, result.status)
        assertEquals(0, result.value!!.compareTo(BigDecimal("13.00")))
    }

    @Test
    fun `BR-13 caps sequence mark at maxScore`() {
        val test = assessment("a1", AssessmentType.TEST)
        val bonus = assessment("a2", AssessmentType.BONUS)
        val columns = listOf(col(test, "19.00"), col(bonus, "3.00"))
        val result = SequenceMarkCalculator.totalize(
            TotalizationRule(maxScore = BigDecimal("20"), capAtMax = true),
            columns
        )
        assertEquals(0, result.value!!.compareTo(BigDecimal("20.00")))
    }

    @Test
    fun `zero scored is counted unlike pending`() {
        val a1 = assessment("a1", AssessmentType.TEST)
        val a2 = assessment("a2", AssessmentType.TEST)
        val columns = listOf(
            col(a1, "10.00"),
            SequenceMarkCalculator.InputColumn(
                a2,
                Grade(
                    schoolId = "school-1",
                    academicYearId = "y",
                    classId = "c",
                    classSubjectId = "cs",
                    sessionId = "s",
                    assessmentId = "a2",
                    studentId = "stu",
                    enrollmentId = "e",
                    status = GradeStatus.SCORED,
                    value = BigDecimal.ZERO
                )
            )
        )
        val result = SequenceMarkCalculator.totalize(TotalizationRule(), columns)
        assertEquals(0, result.value!!.compareTo(BigDecimal("5.00")))
    }

    @Test
    fun `manual formula uses sequence mark column`() {
        val mark = assessment("a1", AssessmentType.SEQUENCE_MARK)
        val result = SequenceMarkCalculator.totalize(
            TotalizationRule(formula = TotalizationFormula.MANUAL),
            listOf(col(mark, "14.55"))
        )
        assertTrue(result.status == GradeStatus.SCORED)
        assertEquals(0, result.value!!.compareTo(BigDecimal("14.55")))
    }

    private fun assessment(
        id: String,
        type: AssessmentType,
        max: String = "20"
    ) = Assessment(
        id = id,
        schoolId = "school-1",
        academicYearId = "y",
        classId = "c",
        classSubjectId = "cs",
        sessionId = "s",
        sheetId = "sheet-1",
        sessionNumber = 1,
        title = "Col",
        type = type,
        maxScore = BigDecimal(max)
    )

    private fun col(assessment: Assessment, value: String) =
        SequenceMarkCalculator.InputColumn(
            assessment,
            Grade(
                schoolId = "school-1",
                academicYearId = "y",
                classId = "c",
                classSubjectId = "cs",
                sessionId = "s",
                assessmentId = assessment.id!!,
                studentId = "stu",
                enrollmentId = "e",
                status = GradeStatus.SCORED,
                value = BigDecimal(value)
            )
        )
}
