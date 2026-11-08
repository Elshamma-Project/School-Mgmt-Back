package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.RankingPolicy
import com.kobe.smels.model.shared.Sex
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportSheetCalculatorTest {

    @Test
    fun `BR-72 competition ranks 1 2 2 4 on exact marks`() {
        val ranks = ReportSheetCalculator.ranks(
            listOf(
                ReportSheetCalculator.ScoredStudent("a", BigDecimal("15.00")),
                ReportSheetCalculator.ScoredStudent("b", BigDecimal("14.00")),
                ReportSheetCalculator.ScoredStudent("c", BigDecimal("14.00")),
                ReportSheetCalculator.ScoredStudent("d", BigDecimal("10.00")),
                ReportSheetCalculator.ScoredStudent("e", null)
            ),
            RankingPolicy.COMPETITION
        )
        assertEquals(1, ranks["a"])
        assertEquals(2, ranks["b"])
        assertEquals(2, ranks["c"])
        assertEquals(4, ranks["d"])
        assertNull(ranks["e"])
    }

    @Test
    fun `BR-73 weighted mark is mark times coefficient`() {
        val w = ReportSheetCalculator.weightedMark(BigDecimal("12.50"), BigDecimal("2"))
        assertEquals(0, w!!.compareTo(BigDecimal("25.00")))
        assertNull(ReportSheetCalculator.weightedMark(null, BigDecimal.ONE))
    }

    @Test
    fun `BR-74 PENDING has no appreciation never zero`() {
        val scale = ReportSheetCalculator.defaultLetterScale()
        assertNull(ReportSheetCalculator.appreciation(null, GradeStatus.PENDING, scale))
        assertNull(ReportSheetCalculator.appreciation(BigDecimal.ZERO, GradeStatus.PENDING, scale))
        assertEquals("B", ReportSheetCalculator.appreciation(BigDecimal("12.00"), GradeStatus.SCORED, scale))
        assertEquals("A", ReportSheetCalculator.appreciation(BigDecimal("16.00"), GradeStatus.SCORED, scale))
    }

    @Test
    fun `BR-71 headcount warns when sex missing`() {
        val ok = ReportSheetCalculator.headcounts(listOf(Sex.MALE, Sex.FEMALE, Sex.MALE))
        assertEquals(3, ok.headcount)
        assertEquals(2, ok.boysCount)
        assertEquals(1, ok.girlsCount)
        assertFalse(ok.sexCountWarning)

        val warn = ReportSheetCalculator.headcounts(listOf(Sex.MALE, null, Sex.FEMALE))
        assertEquals(3, warn.headcount)
        assertEquals(1, warn.boysCount)
        assertEquals(1, warn.girlsCount)
        assertTrue(warn.sexCountWarning)
    }

    @Test
    fun `Q-35 page count allows two pages beyond 45`() {
        assertEquals(1, ReportSheetCalculator.expectedPageCount(45, 45))
        assertEquals(2, ReportSheetCalculator.expectedPageCount(46, 45))
        assertEquals(2, ReportSheetCalculator.expectedPageCount(90, 45))
    }
}
