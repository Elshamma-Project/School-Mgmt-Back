package com.kobe.smels.service.reporting

import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportGroup
import com.kobe.smels.model.reporting.ReportLine
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Sex
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.Barcode128
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PDF bulletin A4 portrait, une seule page (Z1–Z10, docs/01 + maquette KOBE).
 * Ajuste la police entre [ReportTemplate.minFontSizePt] et maxFontSizePt.
 */
@Service
class ReportCardPdfService {

    fun render(card: ReportCard, template: ReportTemplate, academicYearLabel: String): ByteArray {
        var fontSize = template.maxFontSizePt
        var lastError: String? = null
        while (fontSize >= template.minFontSizePt - 0.01) {
            val bytes = build(card, template, academicYearLabel, fontSize.toFloat())
            val pages = pageCount(bytes)
            if (pages == 1) return bytes
            lastError = "Content spans $pages pages at ${"%.1f".format(fontSize)}pt"
            fontSize -= 0.25
        }
        throw BusinessRuleException(
            "REPORT_CARD_OVERFLOW",
            "Bulletin does not fit on one A4 page even at minimum font size (${template.minFontSizePt}pt). $lastError"
        )
    }

    fun pageCount(pdfBytes: ByteArray): Int =
        com.lowagie.text.pdf.PdfReader(pdfBytes).use { it.numberOfPages }

    private fun build(
        card: ReportCard,
        template: ReportTemplate,
        academicYearLabel: String,
        fontSize: Float
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 22f, 22f, 18f, 16f)
        val writer = PdfWriter.getInstance(document, out)
        document.open()

        val lang = card.language
        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize)
        val regular = FontFactory.getFont(FontFactory.HELVETICA, fontSize)
        val small = FontFactory.getFont(FontFactory.HELVETICA, (fontSize - 0.8f).coerceAtLeast(5f))
        val smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize - 0.8f).coerceAtLeast(5f))
        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize + 1.5f)

        // Z1
        document.add(headerTable(card, academicYearLabel, bold, small, titleFont, lang))
        document.add(Paragraph(" ", small))

        // Z2
        document.add(identityTable(card, bold, regular, small, lang))
        document.add(Paragraph(" ", small))

        // Z3 + Z4
        document.add(marksTable(card, template, bold, regular, small, smallBold, lang, fontSize))
        document.add(Paragraph(" ", small))

        // Z5–Z9
        document.add(bottomBand(card, template, bold, regular, small, lang))
        document.add(Paragraph(" ", small))

        // Z10
        document.add(footerBlock(card, writer, bold, small, lang))

        val legendText = when (lang) {
            Language.FR -> template.legend?.fr
            Language.EN -> template.legend?.en
        }
        if (!legendText.isNullOrBlank()) {
            document.add(Paragraph(legendText, small))
        }

        document.close()
        return out.toByteArray()
    }

    private fun headerTable(
        card: ReportCard,
        yearLabel: String,
        bold: Font,
        small: Font,
        titleFont: Font,
        lang: Language
    ): PdfPTable {
        val table = PdfPTable(floatArrayOf(1f, 0.35f, 1f))
        table.widthPercentage = 100f
        val fr = Paragraph().apply {
            add(Chunk("RÉPUBLIQUE DU CAMEROUN\n", small))
            add(Chunk("Paix – Travail – Patrie\n", small))
            add(Chunk("MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES\n", small))
            add(Chunk(card.classInfo.className + "\n", small))
        }
        val en = Paragraph().apply {
            add(Chunk("REPUBLIC OF CAMEROON\n", small))
            add(Chunk("Peace – Work – Fatherland\n", small))
            add(Chunk("MINISTRY OF SECONDARY EDUCATION\n", small))
        }
        addBorderless(table, fr, Element.ALIGN_LEFT)
        addBorderless(table, Paragraph(Chunk("LOGO", bold)), Element.ALIGN_CENTER)
        addBorderless(table, en, Element.ALIGN_RIGHT)

        val title = when {
            card.period.type == ReportPeriodType.TERM ->
                if (lang == Language.FR) "BULLETIN DE TRIMESTRE ${card.period.number}"
                else "TERM ${card.period.number} REPORT CARD"
            else ->
                if (lang == Language.FR) "BULLETIN ANNUEL DE NOTES"
                else "ANNUAL REPORT CARD"
        }
        val titleCell = PdfPCell(Phrase("$title  —  $yearLabel", titleFont))
        titleCell.colspan = 3
        titleCell.horizontalAlignment = Element.ALIGN_CENTER
        titleCell.border = 0
        titleCell.setPadding(4f)
        table.addCell(titleCell)
        return table
    }

    private fun identityTable(
        card: ReportCard,
        bold: Font,
        regular: Font,
        small: Font,
        lang: Language
    ): PdfPTable {
        val table = PdfPTable(4)
        table.widthPercentage = 100f
        val s = card.student
        val fullName = listOf(s.lastName, s.firstName).filter { it.isNotBlank() }.joinToString(" ")
        fun label(fr: String, en: String) = if (lang == Language.FR) fr else en
        row(table, label("Noms et prénoms", "Full names"), fullName, bold, regular)
        row(table, label("Classe", "Class"), card.classInfo.className, bold, regular)
        row(table, label("Matricule", "Matricule"), s.matricule, bold, regular)
        row(
            table,
            label("Sexe", "Sex"),
            when (s.sex) {
                Sex.MALE -> if (lang == Language.FR) "Masculin" else "Male"
                Sex.FEMALE -> if (lang == Language.FR) "Féminin" else "Female"
                null -> ""
            },
            bold,
            regular
        )
        row(
            table,
            label("Redoublant", "Repeater"),
            if (s.repeater) label("Oui", "Yes") else label("Non", "No"),
            bold,
            regular
        )
        row(
            table,
            label("Effectif", "N° on roll"),
            card.classInfo.effectif.toString(),
            bold,
            regular
        )
        row(
            table,
            label("Prof. principal", "Class master"),
            card.classInfo.mainTeacherName ?: "",
            bold,
            regular
        )
        if (lang == Language.EN && !s.photoFileId.isNullOrBlank()) {
            row(table, "Photo", "[photo]", bold, small)
        }
        return table
    }

    private fun marksTable(
        card: ReportCard,
        template: ReportTemplate,
        bold: Font,
        regular: Font,
        small: Font,
        smallBold: Font,
        lang: Language,
        fontSize: Float
    ): PdfPTable {
        val seqCols = template.sequenceColumns
        val colCount = 1 + seqCols.size + 4
        val widths = FloatArray(colCount)
        widths[0] = 2.6f
        for (i in 1..seqCols.size) widths[i] = 0.7f
        widths[seqCols.size + 1] = 0.8f
        widths[seqCols.size + 2] = 0.6f
        widths[seqCols.size + 3] = 0.8f
        widths[seqCols.size + 4] = 0.7f
        val table = PdfPTable(widths)
        table.widthPercentage = 100f
        table.headerRows = 1

        val headers = mutableListOf(if (lang == Language.FR) "Discipline" else "Subject")
        seqCols.forEach { n ->
            headers += if (template.periodType == ReportPeriodType.TERM) "Eval$n" else "DEV$n"
        }
        headers += if (lang == Language.FR) "Moy/20" else "Av/20"
        headers += if (lang == Language.FR) "Coef" else "Coef"
        headers += "Total"
        headers += if (lang == Language.FR) "App." else "Grade"
        headers.forEach { h ->
            val c = PdfPCell(Phrase(h, smallBold))
            c.horizontalAlignment = Element.ALIGN_CENTER
            c.backgroundColor = Color(230, 230, 230)
            c.setPadding(1.5f)
            table.addCell(c)
        }

        card.groups.sortedBy { it.displayOrder }.forEach { group ->
            group.lines.forEach { line -> addSubjectRow(table, line, seqCols, small, regular, template) }
            addGroupRow(table, group, seqCols.size, smallBold, lang)
        }
        return table
    }

    private fun addSubjectRow(
        table: PdfPTable,
        line: ReportLine,
        seqCols: List<Int>,
        small: Font,
        regular: Font,
        template: ReportTemplate
    ) {
        val name = Paragraph()
        name.add(Chunk(line.subjectName, regular))
        if (!line.teacherName.isNullOrBlank()) {
            name.add(Chunk("\n${line.teacherName}", small))
        }
        if (template.showCompetency && !line.competency.isNullOrBlank()) {
            name.add(Chunk("\n${line.competency}", small))
        }
        val nameCell = PdfPCell(name)
        nameCell.setPadding(1.2f)
        table.addCell(nameCell)

        val bySession = line.marks.associateBy { it.sessionNumber }
        seqCols.forEach { n ->
            val mark = bySession[n]
            val text = when {
                mark?.value != null -> fmt(mark.value!!)
                else -> "-"
            }
            cell(table, text, small)
        }
        cell(table, line.average?.let { fmt(it) } ?: "-", small)
        cell(table, fmt(line.coefficient), small)
        cell(table, line.total?.let { fmt(it) } ?: "-", small)
        var app = line.appreciationLabel ?: line.appreciationCode ?: ""
        if (template.showSubjectRank && line.subjectRank != null) {
            app = listOf(app, ordinal(line.subjectRank!!)).filter { it.isNotBlank() }.joinToString(" ")
        }
        cell(table, app, small)
    }

    private fun addGroupRow(
        table: PdfPTable,
        group: ReportGroup,
        seqCount: Int,
        font: Font,
        lang: Language
    ) {
        val label = if (lang == Language.FR) "TOTAL ${group.name}" else "TOTAL ${group.name}"
        val c0 = PdfPCell(Phrase(label, font))
        c0.colspan = 1 + seqCount
        c0.backgroundColor = Color(255, 220, 180)
        c0.setPadding(1.5f)
        table.addCell(c0)
        cellBg(table, "", font, Color(255, 220, 180))
        cellBg(table, fmt(group.totalCoefficient), font, Color(255, 220, 180))
        cellBg(table, group.totalPoints?.let { fmt(it) } ?: "-", font, Color(255, 220, 180))
        val moy = group.average?.let {
            (if (lang == Language.FR) "Moy: " else "Av: ") + fmt(it)
        } ?: ""
        cellBg(table, moy, font, Color(255, 220, 180))
    }

    private fun bottomBand(
        card: ReportCard,
        template: ReportTemplate,
        bold: Font,
        regular: Font,
        small: Font,
        lang: Language
    ): PdfPTable {
        val table = PdfPTable(5)
        table.widthPercentage = 100f
        val o = card.overall
        val z5 = buildString {
            append(if (lang == Language.FR) "Moyenne: " else "Average: ")
            append(o.average?.let { fmt(it) } ?: "-")
            append(" / 20\n")
            append(if (lang == Language.FR) "Rang: " else "Rank: ")
            if (o.rankStatus.name == "RANKED" && o.rank != null) append("${o.rank} / ${o.rankedCount}")
            else append("-")
            append("\n")
            append(o.mentionLabel ?: o.mentionCode ?: "")
            if (o.subjectsPassed != null) {
                append("\nPASSED IN ${o.subjectsPassed}/${o.subjectsCounted}")
            }
            if (template.showEffortSubjects && o.effortSubjectNames.isNotEmpty()) {
                append("\nMAKE EFFORT IN: ${o.effortSubjectNames.joinToString(", ")}")
            }
            if (template.showTotalPointsOverMax && o.totalPoints != null) {
                append("\n${fmt(o.totalPoints!!)} / ${fmt(o.totalCoefficient.multiply(BigDecimal("20")))}")
            }
        }
        val z6 = buildString {
            append(if (lang == Language.FR) "Rappels\n" else "Term recap\n")
            card.termRecaps.forEach { t ->
                append("T${t.termNumber}: ${t.average?.let { fmt(it) } ?: "-"}")
                if (t.rank != null) append(" (${t.rank})")
                append("\n")
            }
        }
        val z7 = buildString {
            val p = card.classProfile
            append(if (lang == Language.FR) "Profil classe\n" else "Class profile\n")
            if (p != null) {
                append(">= ${fmt(p.threshold)}: ${p.countAtOrAboveThreshold}\n")
                append("Avg: ${p.classAverage?.let { fmt(it) } ?: "-"}\n")
                append("%: ${p.successRate?.let { fmt(it) } ?: "-"}\n")
                append("Max: ${p.highestAverage?.let { fmt(it) } ?: "-"}\n")
                append("Min: ${p.lowestAverage?.let { fmt(it) } ?: "-"}")
            }
        }
        val rev = card.review
        val z8 = buildString {
            append(if (lang == Language.FR) "Travail\n" else "Work\n")
            append("Honour: ${rev?.distinction ?: "-"}\n")
            append("Warn: ${rev?.workSanction ?: "-"}")
        }
        val z9 = buildString {
            append(if (lang == Language.FR) "Conduite\n" else "Conduct\n")
            append("Abs: ${rev?.absenceHoursTotal ?: 0}h\n")
            append("NJ: ${rev?.absenceHoursUnjustified ?: 0}h\n")
            append("Late: ${rev?.lateCount ?: 0}\n")
            append("Warn: ${rev?.conductSanction ?: "-"}")
        }
        listOf(z5, z6, z7, z8, z9).forEach {
            val c = PdfPCell(Phrase(it, small))
            c.setPadding(3f)
            table.addCell(c)
        }
        return table
    }

    private fun footerBlock(
        card: ReportCard,
        writer: PdfWriter,
        bold: Font,
        small: Font,
        lang: Language
    ): PdfPTable {
        val table = PdfPTable(3)
        table.widthPercentage = 100f
        val visaParent = if (lang == Language.FR) "Visa du parent" else "Parent visa"
        val decision = if (lang == Language.FR) "Décision du conseil" else "Council decision"
        val visaChef = buildString {
            append(if (lang == Language.FR) "Visa du chef" else "Head visa")
            append("\n")
            card.signature?.let { sig ->
                append(listOfNotNull(sig.place, sig.date?.toString(), sig.signatoryTitle, sig.signatoryName)
                    .joinToString(" — "))
            }
        }
        listOf(visaParent, decision, visaChef).forEach {
            val c = PdfPCell(Phrase("$it\n\n\n", small))
            c.fixedHeight = 48f
            table.addCell(c)
        }
        val disclaimer = if (lang == Language.FR) {
            "Le bulletin est délivré sans rature ni surcharge."
        } else {
            "This report card is issued without alteration."
        }
        val disc = PdfPCell(Phrase(disclaimer, small))
        disc.colspan = 2
        disc.border = 0
        table.addCell(disc)

        val barcode = Barcode128()
        barcode.code = card.verificationCode
        barcode.barHeight = 18f
        barcode.x = 0.8f
        val img = barcode.createImageWithBarcode(writer.directContent, null, null)
        img.scaleAbsolute(120f, 28f)
        val barCell = PdfPCell(img)
        barCell.border = 0
        barCell.horizontalAlignment = Element.ALIGN_RIGHT
        table.addCell(barCell)

        val stamped = card.generatedAt.atZone(ZoneId.of("Africa/Douala"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        val stamp = PdfPCell(Phrase("${card.verificationCode}  |  $stamped", small))
        stamp.colspan = 3
        stamp.border = 0
        table.addCell(stamp)
        return table
    }

    private fun row(table: PdfPTable, label: String, value: String, bold: Font, regular: Font) {
        val l = PdfPCell(Phrase(label, bold))
        l.setPadding(1.5f)
        table.addCell(l)
        val v = PdfPCell(Phrase(value, regular))
        v.colspan = 3
        v.setPadding(1.5f)
        table.addCell(v)
    }

    private fun cell(table: PdfPTable, text: String, font: Font) {
        val c = PdfPCell(Phrase(text, font))
        c.horizontalAlignment = Element.ALIGN_CENTER
        c.setPadding(1.2f)
        table.addCell(c)
    }

    private fun cellBg(table: PdfPTable, text: String, font: Font, bg: Color) {
        val c = PdfPCell(Phrase(text, font))
        c.horizontalAlignment = Element.ALIGN_CENTER
        c.backgroundColor = bg
        c.setPadding(1.2f)
        table.addCell(c)
    }

    private fun addBorderless(table: PdfPTable, content: Paragraph, align: Int) {
        val c = PdfPCell()
        c.addElement(content)
        c.border = 0
        c.horizontalAlignment = align
        table.addCell(c)
    }

    private fun fmt(v: BigDecimal): String =
        v.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun ordinal(n: Int): String {
        val mod100 = n % 100
        val suffix = when {
            mod100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
