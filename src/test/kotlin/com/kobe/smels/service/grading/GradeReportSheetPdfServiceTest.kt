package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.grading.GradeReportSheetLine
import com.kobe.smels.model.grading.ReportSheetSequenceCell
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.repository.grading.GradeReportSheetRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.lowagie.text.pdf.PdfReader
import org.mockito.kotlin.mock
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradeReportSheetPdfServiceTest {

    private val service = GradeReportSheetPdfService(
        mock<GradeReportSheetRepository>(),
        mock<GradingSchemeRepository>(),
        mock<SchoolClassApi>()
    )

    @Test
    fun `BR-75 PDF has 1 page up to 45 lines`() {
        val sheet = sampleSheet(45)
        val bytes = invokeBuild(sheet, 45)
        PdfReader(bytes).use { reader ->
            assertEquals(1, reader.numberOfPages)
        }
    }

    @Test
    fun `Q-35 PDF has 2 pages when more than maxRows`() {
        val sheet = sampleSheet(46)
        val bytes = invokeBuild(sheet, 45)
        PdfReader(bytes).use { reader ->
            assertEquals(2, reader.numberOfPages)
        }
    }

    @Test
    fun `pageCount helper matches calculator`() {
        assertEquals(1, service.pageCountFor(45, 45))
        assertEquals(2, service.pageCountFor(46, 45))
    }

    private fun invokeBuild(sheet: GradeReportSheet, maxRows: Int): ByteArray {
        val method = GradeReportSheetPdfService::class.java.getDeclaredMethod(
            "buildPdf",
            GradeReportSheet::class.java,
            Language::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(service, sheet, Language.FR, maxRows) as ByteArray
    }

    private fun sampleSheet(n: Int) = GradeReportSheet(
        id = "rs1",
        schoolId = "school-1",
        academicYearId = "year-1",
        classId = "class-1",
        classSubjectId = "cs-1",
        termNumber = 1,
        teacherLabel = "Enseignant Test",
        subjectLabel = LocalizedText(fr = "Mathématiques", en = "Mathematics"),
        classLabel = "6ème A",
        coefficient = BigDecimal("2"),
        headcount = n,
        boysCount = n / 2,
        girlsCount = n - n / 2,
        sequence1Number = 1,
        sequence2Number = 2,
        lines = (1..n).map { i ->
            GradeReportSheetLine(
                rollNumber = i,
                studentId = "stu-$i",
                studentLabel = "Élève ${i.toString().padStart(2, '0')}",
                sex = if (i % 2 == 0) Sex.FEMALE else Sex.MALE,
                sequence1 = ReportSheetSequenceCell(
                    mark = BigDecimal("12.00"),
                    coefficient = BigDecimal("2"),
                    weightedMark = BigDecimal("24.00"),
                    rank = 1,
                    appreciation = "B"
                ),
                sequence2 = ReportSheetSequenceCell()
            )
        }
    ).also { assertTrue(it.lines.size == n) }
}
