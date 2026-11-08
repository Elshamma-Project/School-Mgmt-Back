package com.kobe.smels.service.grading

import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.shared.Language
import com.kobe.smels.repository.grading.GradeReportSheetRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.shared.security.CurrentUser
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream

/**
 * PDF A4 paysage de la fiche de report (BR-75).
 * Jusqu'à reportSheetMaxRowsPerPage lignes / page ; au-delà pages suivantes avec en-tête répété.
 */
@Service
class GradeReportSheetPdfService(
    private val gradeReportSheetRepository: GradeReportSheetRepository,
    private val gradingSchemeRepository: GradingSchemeRepository,
    private val schoolClassApi: SchoolClassApi
) {
    fun renderPdf(
        currentUser: CurrentUser,
        id: String,
        languageOverride: Language? = null,
        schoolIdOverride: String? = null
    ): Mono<ByteArray> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                GradingAccess.assertCanReadReportSheet(currentUser, sheet.teacherId)
                schoolClassApi.getSummary(schoolId, sheet.classId).flatMap { clazz ->
                    val lang = languageOverride ?: clazz.defaultLanguage
                    resolveMaxRows(schoolId, clazz.gradingSchemeId).map { maxRows ->
                        buildPdf(sheet, lang, maxRows)
                    }
                }
            }
    }

    fun pageCountFor(lineCount: Int, maxRowsPerPage: Int): Int =
        ReportSheetCalculator.expectedPageCount(lineCount, maxRowsPerPage)

    private fun resolveMaxRows(schoolId: String, schemeId: String?): Mono<Int> =
        if (schemeId != null) {
            gradingSchemeRepository.findByIdAndSchoolId(schemeId, schoolId)
                .map { it.reportSheetMaxRowsPerPage }
                .defaultIfEmpty(45)
        } else {
            gradingSchemeRepository.findBySchoolIdAndActiveTrue(schoolId).next()
                .map { it.reportSheetMaxRowsPerPage }
                .defaultIfEmpty(45)
        }

    private fun buildPdf(sheet: GradeReportSheet, language: Language, maxRows: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate(), 18f, 18f, 14f, 16f)
        PdfWriter.getInstance(document, out)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)
        val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6f)
        val cellFont = FontFactory.getFont(FontFactory.HELVETICA, 5.5f)
        val smallFont = FontFactory.getFont(FontFactory.HELVETICA, 5.5f)

        val chunks = sheet.lines.chunked(maxRows.coerceAtLeast(1))
        val pages = if (chunks.isEmpty()) listOf(emptyList()) else chunks

        pages.forEachIndexed { pageIndex, pageLines ->
            if (pageIndex > 0) document.newPage()
            document.add(
                Paragraph(
                    when (language) {
                        Language.FR -> "FICHE DE REPORT DE NOTES / GRADE REPORT SHEET"
                        Language.EN -> "GRADE REPORT SHEET / FICHE DE REPORT DE NOTES"
                    },
                    titleFont
                ).also { it.alignment = Element.ALIGN_CENTER }
            )
            document.add(
                Paragraph(
                    metaLine(sheet, language),
                    smallFont
                )
            )
            document.add(Paragraph(" ", smallFont))

            val table = PdfPTable(floatArrayOf(0.5f, 2.4f, 0.5f, 0.7f, 0.5f, 0.7f, 0.5f, 0.5f, 0.7f, 0.5f, 0.7f, 0.5f, 0.5f))
            table.widthPercentage = 100f
            table.headerRows = 1
            addHeaderRow(table, headerFont, language, sheet)
            pageLines.forEach { line ->
                addCell(table, line.rollNumber?.toString() ?: "", cellFont)
                addCell(table, line.studentLabel, cellFont, Element.ALIGN_LEFT)
                addCell(table, sexLabel(line.sex, language), cellFont)
                addCell(table, formatMark(line.sequence1.mark), cellFont)
                addCell(table, formatMark(line.sequence1.coefficient), cellFont)
                addCell(table, formatMark(line.sequence1.weightedMark), cellFont)
                addCell(table, line.sequence1.rank?.toString() ?: "", cellFont)
                addCell(table, line.sequence1.displayedAppreciation() ?: "", cellFont)
                addCell(table, formatMark(line.sequence2.mark), cellFont)
                addCell(table, formatMark(line.sequence2.coefficient), cellFont)
                addCell(table, formatMark(line.sequence2.weightedMark), cellFont)
                addCell(table, line.sequence2.rank?.toString() ?: "", cellFont)
                addCell(table, line.sequence2.displayedAppreciation() ?: "", cellFont)
            }
            document.add(table)

            if (pageIndex == pages.lastIndex) {
                document.add(
                    Paragraph(
                        when (language) {
                            Language.FR ->
                                "Visas : Enseignant ____________  Surveillant Général ____________  Principal ____________"
                            Language.EN ->
                                "Visas: Teacher ____________  Supervisor ____________  Principal ____________"
                        },
                        smallFont
                    )
                )
                document.add(
                    Paragraph(
                        when (language) {
                            Language.FR ->
                                "Toute rature doit être visée par le Principal. À déposer au plus tard 72 h après la composition de chaque séquence."
                            Language.EN ->
                                "Any alteration must be endorsed by the Principal. Submit within 72 h after each sequence exam."
                        },
                        smallFont
                    )
                )
            }
        }

        document.close()
        return out.toByteArray()
    }

    private fun metaLine(sheet: GradeReportSheet, language: Language): String {
        val subject = when (language) {
            Language.FR -> sheet.subjectLabel.fr ?: sheet.subjectLabel.en
            Language.EN -> sheet.subjectLabel.en ?: sheet.subjectLabel.fr
        } ?: ""
        return buildString {
            append(if (language == Language.FR) "Classe: " else "Class: ")
            append(sheet.classLabel ?: "")
            append("  |  ")
            append(if (language == Language.FR) "Enseignant: " else "Teacher: ")
            append(sheet.teacherLabel ?: "")
            append("  |  ")
            append(if (language == Language.FR) "Matière: " else "Subject: ")
            append(subject)
            append("  |  Coef: ")
            append(sheet.coefficient.stripTrailingZeros().toPlainString())
            append("  |  N° T")
            append(sheet.termNumber)
            append("  |  ")
            append(if (language == Language.FR) "Effectif: " else "Roll: ")
            append(sheet.headcount)
            append(" (")
            append(if (language == Language.FR) "G: " else "B: ")
            append(sheet.boysCount)
            append(if (language == Language.FR) " F: " else " G: ")
            append(sheet.girlsCount)
            append(")")
            if (sheet.sexCountWarning) {
                append(if (language == Language.FR) " [sexe manquant]" else " [sex missing]")
            }
            append("  |  ")
            append(if (language == Language.FR) "Dates: " else "Dates: ")
            append(sheet.sequence1ExamDate?.toString() ?: "-")
            append(" / ")
            append(sheet.sequence2ExamDate?.toString() ?: "-")
        }
    }

    private fun addHeaderRow(
        table: PdfPTable,
        font: Font,
        language: Language,
        sheet: GradeReportSheet
    ) {
        val s1 = "S${sheet.sequence1Number ?: 1}"
        val s2 = "S${sheet.sequence2Number ?: 2}"
        listOf(
            "N°",
            if (language == Language.FR) "Noms et prénoms" else "Full names",
            if (language == Language.FR) "Sexe" else "Sex",
            "$s1 Comp/20", "$s1 Coef", "$s1 N×C", "$s1 Rang", "$s1 App",
            "$s2 Comp/20", "$s2 Coef", "$s2 N×C", "$s2 Rang", "$s2 App"
        ).forEach { addCell(table, it, font) }
    }

    private fun addCell(
        table: PdfPTable,
        text: String,
        font: Font,
        align: Int = Element.ALIGN_CENTER
    ) {
        val cell = PdfPCell(Phrase(text, font))
        cell.horizontalAlignment = align
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(0.8f)
        cell.fixedHeight = 9.2f
        table.addCell(cell)
    }

    private fun formatMark(value: java.math.BigDecimal?): String =
        value?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: ""

    private fun sexLabel(sex: com.kobe.smels.model.shared.Sex?, language: Language): String =
        when (sex) {
            com.kobe.smels.model.shared.Sex.MALE -> if (language == Language.FR) "M" else "M"
            com.kobe.smels.model.shared.Sex.FEMALE -> if (language == Language.FR) "F" else "F"
            null -> ""
        }
}
