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
import com.lowagie.text.Rectangle
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
 * PDF bulletin A4 portrait, une seule page (Z1–Z10, maquette KOBE).
 * Palette : #0F3D4C / #1C6E7D / #E6F0F2 / #C98A1B / #B3261E.
 * Ajuste la police entre [ReportTemplate.minFontSizePt] et maxFontSizePt.
 */
@Service
class ReportCardPdfService {

    fun render(
        card: ReportCard,
        template: ReportTemplate,
        academicYearLabel: String,
        passMark: BigDecimal = BigDecimal("10"),
    ): ByteArray {
        var fontSize = template.maxFontSizePt
        var lastError: String? = null
        while (fontSize >= template.minFontSizePt - 0.01) {
            val bytes = build(card, template, academicYearLabel, fontSize.toFloat(), passMark)
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
        fontSize: Float,
        passMark: BigDecimal,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // 8 mm ≈ 22.7 pt
        val margin = 22.7f
        val document = Document(PageSize.A4, margin, margin, margin, margin)
        val writer = PdfWriter.getInstance(document, out)
        document.open()

        val lang = card.language
        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize, INK)
        val regular = FontFactory.getFont(FontFactory.HELVETICA, fontSize, INK)
        val small = FontFactory.getFont(FontFactory.HELVETICA, (fontSize - 0.8f).coerceAtLeast(5.2f), INK)
        val smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize - 0.8f).coerceAtLeast(5.2f), INK)
        val muted = FontFactory.getFont(FontFactory.HELVETICA, (fontSize - 1.2f).coerceAtLeast(5.2f), MUTED)
        val titleWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize + 2f).coerceAtMost(11f), Color.WHITE)
        val headerWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize - 0.6f).coerceAtLeast(5.2f), Color.WHITE)
        val avgFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize + 6f).coerceAtMost(15f), INK)
        val failFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize, FAIL)

        document.add(headerBlock(card, academicYearLabel, bold, small, muted, titleWhite, lang))
        document.add(spacer(3f))
        document.add(identityGrid(card, bold, regular, muted, lang))
        document.add(spacer(4f))
        document.add(
            marksTable(card, template, bold, regular, small, smallBold, headerWhite, failFont, lang, passMark)
        )
        document.add(spacer(4f))
        document.add(resultsBand(card, template, bold, regular, small, muted, avgFont, lang))
        document.add(spacer(3f))
        document.add(workConductBand(card, bold, small, muted, lang))
        document.add(spacer(3f))
        document.add(visasBlock(card, bold, muted, lang))
        document.add(spacer(3f))
        document.add(footerBlock(card, template, writer, muted, small, lang))

        document.close()
        return out.toByteArray()
    }

    private fun headerBlock(
        card: ReportCard,
        yearLabel: String,
        bold: Font,
        small: Font,
        muted: Font,
        titleWhite: Font,
        lang: Language,
    ): PdfPTable {
        val wrap = PdfPTable(1)
        wrap.widthPercentage = 100f

        val top = PdfPTable(floatArrayOf(1f, 0.28f, 1f))
        top.widthPercentage = 100f
        val schoolName = card.classInfo.className
        val fr = Paragraph().apply {
            leading = small.size + 1.2f
            add(Chunk("RÉPUBLIQUE DU CAMEROUN\n", bold))
            add(Chunk("Paix – Travail – Patrie\n", muted))
            add(Chunk("MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES\n", small))
            add(Chunk("DÉLÉGATION RÉGIONALE\n", small))
            add(Chunk("DÉLÉGATION DÉPARTEMENTALE\n", small))
            add(Chunk("$schoolName\n", bold))
            add(Chunk("B.P. / Tél.", muted))
            alignment = Element.ALIGN_CENTER
        }
        val en = Paragraph().apply {
            leading = small.size + 1.2f
            add(Chunk("REPUBLIC OF CAMEROON\n", bold))
            add(Chunk("Peace – Work – Fatherland\n", muted))
            add(Chunk("MINISTRY OF SECONDARY EDUCATION\n", small))
            add(Chunk("REGIONAL DELEGATION\n", small))
            add(Chunk("DIVISIONAL DELEGATION\n", small))
            add(Chunk("NAME OF THE SCHOOL\n", bold))
            add(Chunk("P.O. Box / Tel.", muted))
            alignment = Element.ALIGN_CENTER
        }
        addBorderless(top, fr, Element.ALIGN_CENTER)
        val logo = Paragraph().apply {
            alignment = Element.ALIGN_CENTER
            add(Chunk("LOGO\n", bold))
            add(Chunk("ÉCOLE", muted))
        }
        addBorderless(top, logo, Element.ALIGN_CENTER)
        addBorderless(top, en, Element.ALIGN_CENTER)
        wrap.addCell(borderlessCell(top))

        val titleFr = when {
            card.period.type == ReportPeriodType.TERM ->
                "BULLETIN DE TRIMESTRE ${card.period.number ?: 1}"
            else -> "BULLETIN ANNUEL DE NOTES"
        }
        val titleEn = when {
            card.period.type == ReportPeriodType.TERM ->
                "TERM ${card.period.number ?: 1} REPORT CARD"
            else -> "ANNUAL REPORT CARD"
        }
        val yearPrefix = if (lang == Language.FR) "Année scolaire" else "School year"
        val banner = PdfPTable(floatArrayOf(3.2f, 1f))
        banner.widthPercentage = 100f
        val titleCell = PdfPCell(Phrase("$titleFr  ·  $titleEn", titleWhite))
        titleCell.backgroundColor = INK
        titleCell.border = Rectangle.NO_BORDER
        titleCell.setPadding(6f)
        titleCell.verticalAlignment = Element.ALIGN_MIDDLE
        banner.addCell(titleCell)
        val yearFont = FontFactory.getFont(FontFactory.HELVETICA, small.size, Color(215, 230, 234))
        val yearCell = PdfPCell(Phrase("$yearPrefix $yearLabel", yearFont))
        yearCell.backgroundColor = INK
        yearCell.border = Rectangle.NO_BORDER
        yearCell.horizontalAlignment = Element.ALIGN_RIGHT
        yearCell.verticalAlignment = Element.ALIGN_MIDDLE
        yearCell.setPadding(6f)
        banner.addCell(yearCell)

        val bannerWrap = PdfPCell(banner)
        bannerWrap.border = Rectangle.NO_BORDER
        bannerWrap.setPadding(0f)
        bannerWrap.setPaddingTop(4f)
        wrap.addCell(bannerWrap)
        return wrap
    }

    private fun identityGrid(
        card: ReportCard,
        bold: Font,
        regular: Font,
        muted: Font,
        lang: Language,
    ): PdfPTable {
        val s = card.student
        val fullName = listOf(s.lastName, s.firstName).filter { it.isNotBlank() }.joinToString(" ")
        val birth = buildString {
            s.dateOfBirth?.let { append(it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
            if (!s.placeOfBirth.isNullOrBlank()) {
                if (isNotEmpty()) append(" — ")
                append(s.placeOfBirth)
            }
        }
        val sex = when (s.sex) {
            Sex.MALE -> if (lang == Language.FR) "Masculin" else "Male"
            Sex.FEMALE -> if (lang == Language.FR) "Féminin" else "Female"
            null -> "—"
        }
        fun L(fr: String, en: String) = if (lang == Language.FR) fr else en
        val cells = listOf(
            L("NOMS ET PRÉNOMS", "FULL NAMES") to fullName,
            L("CLASSE", "CLASS") to card.classInfo.className,
            L("MATRICULE", "MATRICULE") to s.matricule,
            L("NÉ(E) LE / À", "DATE / PLACE OF BIRTH") to birth.ifBlank { "—" },
            L("SEXE", "SEX") to sex,
            L("REDOUBLANT(E)", "REPEATER") to if (s.repeater) L("Oui", "Yes") else L("Non", "No"),
            L("PROFESSEUR PRINCIPAL", "CLASS MASTER") to (card.classInfo.mainTeacherName ?: "—"),
            L("EFFECTIF DE LA CLASSE", "N° ON ROLL") to card.classInfo.effectif.toString(),
            L("LANGUE DU BULLETIN", "REPORT LANGUAGE") to if (lang == Language.FR) "Français" else "English",
        )
        val table = PdfPTable(3)
        table.widthPercentage = 100f
        cells.forEach { (label, value) ->
            val p = Paragraph()
            p.leading = muted.size + 1.5f
            p.add(Chunk("$label\n", muted))
            p.add(Chunk(value, bold))
            val c = PdfPCell(p)
            c.borderColor = LINE
            c.setPadding(4f)
            c.setPaddingTop(3f)
            c.setPaddingBottom(3f)
            table.addCell(c)
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
        headerWhite: Font,
        failFont: Font,
        lang: Language,
        passMark: BigDecimal,
    ): PdfPTable {
        val seqCols = template.sequenceColumns
        val extras = listOfNotNull(
            if (template.showSubjectRank) "POS" else null,
            if (template.showSubjectClassAverage) "CL." else null,
            if (template.showSubjectPassedCount) "≥10" else null,
        )
        val headers = mutableListOf(
            if (lang == Language.FR) "DISCIPLINES / ENS." else "SUBJECTS / TCH."
        )
        seqCols.forEachIndexed { i, n ->
            headers += if (template.periodType == ReportPeriodType.TERM) "E${i + 1}" else "D$n"
        }
        headers += if (lang == Language.FR) "MOY" else "AV"
        headers += "COEF"
        headers += if (template.periodType == ReportPeriodType.TERM) "×CF" else "TOT"
        headers += extras
        headers += if (lang == Language.FR) "APP." else "REM."

        // Largeurs relatives : matière + colonnes numériques serrées (A4, sans débordement).
        val widths = FloatArray(headers.size)
        widths[0] = 2.35f
        var idx = 1
        repeat(seqCols.size) {
            widths[idx++] = 0.48f
        }
        widths[idx++] = 0.58f // moy
        widths[idx++] = 0.42f // coef
        widths[idx++] = 0.58f // total
        repeat(extras.size) {
            widths[idx++] = 0.45f
        }
        widths[idx] = 0.5f // app
        val table = PdfPTable(widths)
        table.widthPercentage = 100f
        table.headerRows = 1

        headers.forEachIndexed { index, h ->
            val c = PdfPCell(Phrase(h, headerWhite))
            c.backgroundColor = HEADER
            c.borderColor = HEADER
            c.horizontalAlignment = if (index == 0) Element.ALIGN_LEFT else Element.ALIGN_CENTER
            c.setPadding(1.2f)
            c.setPaddingTop(2f)
            c.setPaddingBottom(2f)
            table.addCell(c)
        }

        card.groups.sortedBy { it.displayOrder }.forEach { group ->
            group.lines.forEach { line ->
                addSubjectRow(table, line, seqCols, template, regular, small, failFont, passMark)
            }
            if (template.showGroupTotals) {
                addGroupRow(table, group, seqCols.size, extras.size, smallBold, lang)
            }
        }
        return table
    }

    private fun addSubjectRow(
        table: PdfPTable,
        line: ReportLine,
        seqCols: List<Int>,
        template: ReportTemplate,
        regular: Font,
        small: Font,
        failFont: Font,
        passMark: BigDecimal,
    ) {
        val name = Paragraph()
        name.leading = regular.size + 1f
        name.add(Chunk(line.subjectName.uppercase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, regular.size, INK)))
        if (!line.teacherName.isNullOrBlank()) {
            name.add(Chunk("\n${line.teacherName}", FontFactory.getFont(FontFactory.HELVETICA, small.size - 0.4f, MUTED)))
        }
        if (template.showCompetency && !line.competency.isNullOrBlank()) {
            name.add(Chunk("\n${line.competency}", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, small.size - 0.6f, MUTED)))
        }
        val nameCell = PdfPCell(name)
        nameCell.borderColor = LINE
        nameCell.setPadding(1.2f)
        table.addCell(nameCell)

        val bySession = line.marks.associateBy { it.sessionNumber }
        seqCols.forEach { n ->
            val mark = bySession[n]?.value
            markCell(table, mark, small, failFont, passMark)
        }
        markCell(table, line.average, small, failFont, passMark)
        centerCell(table, fmt(line.coefficient, 0), small)
        centerCell(table, line.total?.let { fmt(it) } ?: "—", small)
        if (template.showSubjectRank) {
            centerCell(table, line.subjectRank?.let { ordinal(it) } ?: "—", small)
        }
        if (template.showSubjectClassAverage) {
            centerCell(table, line.subjectClassAverage?.let { fmt(it) } ?: "—", small)
        }
        if (template.showSubjectPassedCount) {
            centerCell(table, line.subjectPassedCount?.toString() ?: "—", small)
        }
        centerCell(table, line.appreciationLabel ?: line.appreciationCode ?: "", small)
    }

    private fun addGroupRow(
        table: PdfPTable,
        group: ReportGroup,
        seqCount: Int,
        extraCount: Int,
        font: Font,
        lang: Language,
    ) {
        val label = if (lang == Language.FR) "TOTAL ${group.name}" else "TOTAL ${group.name}"
        val c0 = PdfPCell(Phrase(label.uppercase(), font))
        c0.colspan = 1 + seqCount
        c0.backgroundColor = TINT
        c0.borderColor = INK
        c0.setPadding(2.5f)
        table.addCell(c0)
        cellBg(table, group.average?.let { "${fmt(it)}/20" } ?: "—", font, TINT)
        cellBg(table, fmt(group.totalCoefficient, 0), font, TINT)
        cellBg(table, group.totalPoints?.let { fmt(it) } ?: "—", font, TINT)
        repeat(extraCount + 1) {
            cellBg(table, "", font, TINT)
        }
    }

    private fun resultsBand(
        card: ReportCard,
        template: ReportTemplate,
        bold: Font,
        regular: Font,
        small: Font,
        muted: Font,
        avgFont: Font,
        lang: Language,
    ): PdfPTable {
        val table = PdfPTable(3)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(1f, 1f, 1.15f))
        val o = card.overall

        val resultBody = Paragraph().apply {
            leading = avgFont.size
            add(Chunk(o.average?.let { "${fmt(it)}/20" } ?: "—", avgFont))
            add(Chunk("\n", small))
            add(Chunk(if (lang == Language.FR) "Rang " else "Rank ", muted))
            val rankText = if (o.rankStatus.name == "RANKED" && o.rank != null) {
                "${o.rank}/${o.rankedCount}"
            } else "—"
            add(Chunk(rankText, bold))
            add(Chunk("\n\n", small))
        }
        val mention = o.mentionLabel ?: o.mentionCode ?: ""
        val mentionTable = PdfPTable(1)
        if (mention.isNotBlank()) {
            val pill = PdfPCell(Phrase(mention.uppercase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, small.size, Color.WHITE)))
            pill.backgroundColor = AMBER
            pill.border = Rectangle.NO_BORDER
            pill.horizontalAlignment = Element.ALIGN_CENTER
            pill.setPadding(3f)
            mentionTable.addCell(pill)
        }
        resultBody.add(mentionTable)
        table.addCell(
            framed(
                if (lang == Language.FR) "Résultat annuel" else "Annual result",
                resultBody,
                bold,
                muted,
            )
        )

        val mid = Paragraph().apply { leading = small.size + 1.5f }
        if (template.showTermRecap) {
            mid.add(Chunk(if (lang == Language.FR) "MOY.     RANG\n" else "AVG.     RANK\n", muted))
            card.termRecaps.forEach { t ->
                val label = if (lang == Language.FR) "TRIM ${t.termNumber}" else "TERM ${t.termNumber}"
                mid.add(Chunk("$label   ${t.average?.let { fmt(it) } ?: "—"}   ", regular))
                mid.add(Chunk("${t.rank ?: "—"}/${t.rankedCount ?: o.rankedCount}\n", regular))
            }
            table.addCell(
                framed(
                    if (lang == Language.FR) "Rappels des moyennes" else "Term averages",
                    mid,
                    bold,
                    muted,
                )
            )
        } else {
            mid.add(Chunk(o.totalPoints?.let { fmt(it) } ?: "—", avgFont))
            mid.add(Chunk("\n", small))
            if (o.subjectsPassed != null) {
                mid.add(
                    Chunk(
                        if (lang == Language.FR) "ADMIS EN ${o.subjectsPassed}/${o.subjectsCounted}"
                        else "PASSED IN ${o.subjectsPassed}/${o.subjectsCounted}",
                        small,
                    )
                )
            }
            table.addCell(
                framed(
                    if (lang == Language.FR) "Total points" else "Total points",
                    mid,
                    bold,
                    muted,
                )
            )
        }

        val profile = Paragraph().apply { leading = small.size + 1.8f }
        val p = card.classProfile
        if (template.showClassProfile && p != null) {
            fun line(label: String, value: String) {
                profile.add(Chunk("$label  ", muted))
                profile.add(Chunk("$value\n", bold))
            }
            line(
                if (lang == Language.FR) "Élèves ≥ ${fmt(p.threshold, 0)}" else "Pupils ≥ ${fmt(p.threshold, 0)}",
                p.countAtOrAboveThreshold.toString(),
            )
            line(if (lang == Language.FR) "Moyenne classe" else "Class average", p.classAverage?.let { fmt(it) } ?: "—")
            line("%", p.successRate?.let { "${fmt(it)} %" } ?: "—")
            line(
                if (lang == Language.FR) "Max — min" else "Max — min",
                "${p.highestAverage?.let { fmt(it) } ?: "—"} — ${p.lowestAverage?.let { fmt(it) } ?: "—"}",
            )
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "Profil de la classe" else "Class profile",
                profile,
                bold,
                muted,
            )
        )
        return table
    }

    private fun workConductBand(
        card: ReportCard,
        bold: Font,
        small: Font,
        muted: Font,
        lang: Language,
    ): PdfPTable {
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        val rev = card.review
        val work = Paragraph().apply {
            leading = small.size + 2f
            add(dot(if (lang == Language.FR) "Tableau d'honneur" else "Honour roll", rev?.distinction?.name ?: "—", muted, bold))
            add(dot(if (lang == Language.FR) "Encouragements — félicitations" else "Encouragement — congratulations", "—", muted, bold))
            add(dot(if (lang == Language.FR) "Avertissement — blâme travail" else "Warning — blame (work)", rev?.workSanction?.name ?: "—", muted, bold))
        }
        val conduct = Paragraph().apply {
            leading = small.size + 2f
            add(dot(if (lang == Language.FR) "Absences (h)" else "Absences (h)", "${rev?.absenceHoursTotal ?: 0}", muted, bold))
            add(dot(if (lang == Language.FR) "Retards" else "Lates", "${rev?.lateCount ?: 0}", muted, bold))
            add(dot(if (lang == Language.FR) "Avertissement — blâme conduite" else "Warning — blame (conduct)", rev?.conductSanction?.name ?: "—", muted, bold))
        }
        table.addCell(framed(if (lang == Language.FR) "Travail de l'élève" else "Student's work", work, bold, muted))
        table.addCell(framed(if (lang == Language.FR) "Conduite de l'élève" else "Student's conduct", conduct, bold, muted))
        return table
    }

    private fun visasBlock(
        card: ReportCard,
        bold: Font,
        muted: Font,
        lang: Language,
    ): PdfPTable {
        val table = PdfPTable(3)
        table.widthPercentage = 100f
        fun visa(title: String, footer: String? = null): PdfPCell {
            val p = Paragraph()
            p.add(Chunk(title, bold))
            if (footer != null) {
                p.add(Chunk("\n\n\n$footer", muted))
            } else {
                p.add(Chunk("\n\n\n", muted))
            }
            val c = PdfPCell(p)
            c.borderColor = LINE
            c.fixedHeight = 52f
            c.setPadding(5f)
            return c
        }
        table.addCell(visa(if (lang == Language.FR) "VISA DU PARENT" else "PARENT VISA"))
        table.addCell(visa(if (lang == Language.FR) "DÉCISION DU CONSEIL DE CLASSE" else "CLASS COUNCIL DECISION"))
        val sig = card.signature
        val placeDate = buildString {
            append(sig?.place ?: "Yaoundé")
            append(if (lang == Language.FR) ", le " else ", ")
            append(sig?.date?.toString() ?: "… / … / …")
        }
        table.addCell(
            visa(
                if (lang == Language.FR) "LE CHEF D'ÉTABLISSEMENT" else "THE PRINCIPAL",
                placeDate,
            )
        )
        return table
    }

    private fun footerBlock(
        card: ReportCard,
        template: ReportTemplate,
        writer: PdfWriter,
        muted: Font,
        small: Font,
        lang: Language,
    ): PdfPTable {
        val table = PdfPTable(floatArrayOf(1.1f, 2.2f, 1.2f))
        table.widthPercentage = 100f

        val left = PdfPCell(Phrase("Développé par KOBE Corporation", muted))
        left.border = Rectangle.NO_BORDER
        left.verticalAlignment = Element.ALIGN_BOTTOM
        table.addCell(left)

        val legendText = when (lang) {
            Language.FR -> template.legend?.fr
            Language.EN -> template.legend?.en
        }.orEmpty()
        val disclaimer = if (lang == Language.FR) {
            "Bulletin délivré sans rature ni surcharge"
        } else {
            "Report card issued without alteration"
        }
        val stamped = card.generatedAt.atZone(ZoneId.of("Africa/Douala"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        val center = Paragraph().apply {
            alignment = Element.ALIGN_CENTER
            leading = muted.size + 1.2f
            add(Chunk("$disclaimer\n", muted))
            if (legendText.isNotBlank()) add(Chunk("$legendText\n", muted))
            add(Chunk(if (lang == Language.FR) "Imprimé le $stamped" else "Printed on $stamped", muted))
        }
        val centerCell = PdfPCell()
        centerCell.border = Rectangle.NO_BORDER
        centerCell.addElement(center)
        table.addCell(centerCell)

        val barcode = Barcode128()
        barcode.code = card.verificationCode
        barcode.barHeight = 16f
        barcode.x = 0.7f
        val img = barcode.createImageWithBarcode(writer.directContent, INK, INK)
        img.scaleAbsolute(118f, 26f)
        val right = Paragraph().apply {
            alignment = Element.ALIGN_RIGHT
            add(Chunk(img, 0f, 0f))
            add(Chunk("\n${card.verificationCode}", small))
        }
        val rightCell = PdfPCell()
        rightCell.border = Rectangle.NO_BORDER
        rightCell.horizontalAlignment = Element.ALIGN_RIGHT
        rightCell.addElement(right)
        table.addCell(rightCell)
        return table
    }

    private fun framed(title: String, body: Paragraph, titleFont: Font, muted: Font): PdfPCell {
        val inner = PdfPTable(1)
        inner.widthPercentage = 100f
        val head = PdfPCell(Phrase(title.uppercase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, muted.size, INK)))
        head.backgroundColor = TINT
        head.borderColor = LINE
        head.setPadding(3f)
        inner.addCell(head)
        val content = PdfPCell()
        content.borderColor = LINE
        content.setPadding(5f)
        content.addElement(body)
        inner.addCell(content)
        val outer = PdfPCell(inner)
        outer.border = Rectangle.NO_BORDER
        outer.setPadding(1.5f)
        return outer
    }

    private fun dot(label: String, value: String, muted: Font, bold: Font): Phrase {
        val p = Phrase()
        p.add(Chunk("$label ……………… ", muted))
        p.add(Chunk("$value\n", bold))
        return p
    }

    private fun markCell(
        table: PdfPTable,
        value: BigDecimal?,
        normal: Font,
        failFont: Font,
        passMark: BigDecimal,
    ) {
        if (value == null) {
            centerCell(table, "—", normal)
            return
        }
        val font = if (value.compareTo(passMark) < 0) failFont else normal
        centerCell(table, fmt(value), font)
    }

    private fun centerCell(table: PdfPTable, text: String, font: Font) {
        val c = PdfPCell(Phrase(text, font))
        c.horizontalAlignment = Element.ALIGN_CENTER
        c.verticalAlignment = Element.ALIGN_MIDDLE
        c.borderColor = LINE
        c.setPadding(0.8f)
        c.setPaddingTop(1.2f)
        c.setPaddingBottom(1.2f)
        table.addCell(c)
    }

    private fun cellBg(table: PdfPTable, text: String, font: Font, bg: Color) {
        val c = PdfPCell(Phrase(text, font))
        c.horizontalAlignment = Element.ALIGN_CENTER
        c.backgroundColor = bg
        c.borderColor = INK
        c.setPadding(2f)
        table.addCell(c)
    }

    private fun addBorderless(table: PdfPTable, content: Paragraph, align: Int) {
        val c = PdfPCell()
        c.addElement(content)
        c.border = Rectangle.NO_BORDER
        c.horizontalAlignment = align
        table.addCell(c)
    }

    private fun borderlessCell(inner: PdfPTable): PdfPCell {
        val c = PdfPCell(inner)
        c.border = Rectangle.NO_BORDER
        c.setPadding(0f)
        return c
    }

    private fun spacer(height: Float): Paragraph {
        val p = Paragraph(" ")
        p.leading = height
        return p
    }

    private fun fmt(v: BigDecimal, scale: Int = 2): String {
        val scaled = v.setScale(scale, RoundingMode.HALF_UP)
        if (scale == 0) return scaled.toPlainString()
        // Compact : 12 ou 12,5 (évite 12,00 sur colonnes étroites A4).
        val stripped = scaled.stripTrailingZeros()
        val plain = if (stripped.scale() <= 0) stripped.toBigInteger().toString() else stripped.toPlainString()
        return plain.replace('.', ',')
    }

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

    companion object {
        private val INK = Color(0x0F, 0x3D, 0x4C)
        private val HEADER = Color(0x1C, 0x6E, 0x7D)
        private val TINT = Color(0xE6, 0xF0, 0xF2)
        private val AMBER = Color(0xC9, 0x8A, 0x1B)
        private val FAIL = Color(0xB3, 0x26, 0x1E)
        private val MUTED = Color(0x55, 0x70, 0x7A)
        private val LINE = Color(0x9D, 0xB4, 0xBB)
    }
}
