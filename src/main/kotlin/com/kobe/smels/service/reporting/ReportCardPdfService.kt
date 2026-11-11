package com.kobe.smels.service.reporting

import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportGroup
import com.kobe.smels.model.reporting.ReportLine
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.reporting.SanctionLevel
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.Image
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
        context: BulletinPdfContext = BulletinPdfContext(),
    ): ByteArray {
        var fontSize = template.maxFontSizePt
        var lastError: String? = null
        while (fontSize >= template.minFontSizePt - 0.01) {
            val bytes = build(card, template, academicYearLabel, fontSize.toFloat(), passMark, context)
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
        context: BulletinPdfContext,
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

        document.add(headerBlock(card, academicYearLabel, context, bold, small, muted, titleWhite, lang))
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
        context: BulletinPdfContext,
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
        fun columnParagraph(lines: List<String>): Paragraph = Paragraph().apply {
            leading = small.size + 1.2f
            alignment = Element.ALIGN_CENTER
            lines.forEachIndexed { index, line ->
                val isLast = index == lines.lastIndex
                val isName = index == lines.lastIndex - if (lines.lastOrNull()?.contains("B.P.") == true ||
                    lines.lastOrNull()?.contains("P.O.") == true ||
                    lines.lastOrNull()?.contains("Tél.") == true ||
                    lines.lastOrNull()?.contains("Tel.") == true
                ) 1 else 0
                val font = when {
                    index == 0 || isName -> bold
                    isLast && (line.startsWith("B.P.") || line.startsWith("P.O.") || line.startsWith("Tél.") || line.startsWith("Tel.")) -> muted
                    index == 1 -> muted
                    else -> small
                }
                add(Chunk(if (index < lines.lastIndex) "$line\n" else line, font))
            }
        }
        addBorderless(top, columnParagraph(context.headerLinesFr()), Element.ALIGN_CENTER)
        val logoCell = PdfPCell()
        logoCell.border = Rectangle.NO_BORDER
        logoCell.horizontalAlignment = Element.ALIGN_CENTER
        logoCell.verticalAlignment = Element.ALIGN_MIDDLE
        val logoBytes = context.logoBytes
        if (logoBytes != null && logoBytes.isNotEmpty()) {
            runCatching {
                val img = Image.getInstance(logoBytes)
                img.scaleToFit(56f, 56f)
                img.alignment = Element.ALIGN_CENTER
                logoCell.addElement(img)
            }.onFailure {
                logoCell.addElement(Paragraph(" "))
            }
        } else {
            logoCell.addElement(Paragraph(" "))
        }
        top.addCell(logoCell)
        addBorderless(top, columnParagraph(context.headerLinesEn()), Element.ALIGN_CENTER)
        wrap.addCell(borderlessCell(top))

        val isTerm = card.period.type == ReportPeriodType.TERM
        val bannerTitle = when {
            isTerm && lang == Language.FR -> "BULLETIN DE TRIMESTRE · REPORT CARD"
            isTerm -> "REPORT CARD · BULLETIN DE TRIMESTRE"
            lang == Language.FR -> "BULLETIN ANNUEL DE NOTES · ANNUAL REPORT CARD"
            else -> "ANNUAL REPORT CARD · BULLETIN ANNUEL DE NOTES"
        }
        val termNo = card.period.number ?: 1
        val bannerRight = when {
            isTerm && lang == Language.FR ->
                when (termNo) {
                    1 -> "1er TRIMESTRE · $yearLabel"
                    2 -> "2e TRIMESTRE · $yearLabel"
                    else -> "${termNo}e TRIMESTRE · $yearLabel"
                }
            isTerm ->
                when (termNo) {
                    1 -> "FIRST TERM · $yearLabel"
                    2 -> "SECOND TERM · $yearLabel"
                    else -> "THIRD TERM · $yearLabel"
                }
            lang == Language.FR -> "Année scolaire $yearLabel"
            else -> "School year $yearLabel"
        }
        val banner = PdfPTable(floatArrayOf(3.2f, 1.15f))
        banner.widthPercentage = 100f
        val titleCell = PdfPCell(Phrase(bannerTitle, titleWhite))
        titleCell.backgroundColor = INK
        titleCell.border = Rectangle.NO_BORDER
        titleCell.setPadding(6f)
        titleCell.verticalAlignment = Element.ALIGN_MIDDLE
        banner.addCell(titleCell)
        val yearFont = FontFactory.getFont(FontFactory.HELVETICA, small.size, Color(215, 230, 234))
        val yearCell = PdfPCell(Phrase(bannerRight, yearFont))
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
            L("MATRICULE", "REGISTRATION NUMBER") to s.matricule,
            L("NÉ(E) LE / À", "DATE / PLACE OF BIRTH") to birth.ifBlank { "—" },
            L("SEXE", "SEX") to sex,
            L("REDOUBLANT(E)", "REPEATER") to if (s.repeater) L("Oui", "Yes") else L("Non", "No"),
            L("PROFESSEUR PRINCIPAL", "CLASS MASTER / MISTRESS") to (card.classInfo.mainTeacherName ?: "—"),
            L("EFFECTIF DE LA CLASSE", "N° ON ROLL") to card.classInfo.effectif.toString(),
            L("LANGUE DU BULLETIN", "REPORT LANGUAGE") to if (lang == Language.FR) "Français" else "English",
        )
        val table = PdfPTable(3)
        table.widthPercentage = 100f
        cells.forEach { (label, value) ->
            val p = Paragraph()
            p.leading = muted.size + 0.6f
            p.add(Chunk("$label\n", muted))
            p.add(Chunk(value, bold))
            val c = PdfPCell(p)
            c.borderColor = LINE
            c.setPadding(2f)
            c.setPaddingTop(1.5f)
            c.setPaddingBottom(1.5f)
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
        val isTermTpl = template.periodType == ReportPeriodType.TERM
        val extras = listOfNotNull(
            if (template.showSubjectRank) "POS." else null,
            if (template.showSubjectClassAverage) {
                if (lang == Language.FR) "MOY. CL." else "CLASS AV."
            } else null,
            if (template.showSubjectPassedCount) {
                if (lang == Language.FR) "MOY ≥ 10" else "AV ≥ 10"
            } else null,
        )
        val headers = mutableListOf(
            when {
                isTermTpl && lang == Language.FR -> "DISCIPLINES / ENS. / COMPÉTENCE"
                isTermTpl -> "SUBJECTS / TEACHER / COMPETENCY"
                lang == Language.FR -> "DISCIPLINES / ENS."
                else -> "SUBJECTS / TCH."
            }
        )
        seqCols.forEachIndexed { i, n ->
            headers += if (isTermTpl) "EVAL ${i + 1}" else "D$n"
        }
        headers += if (lang == Language.FR) "MOY/20" else "AV/20"
        headers += "COEF"
        headers += when {
            isTermTpl && lang == Language.FR -> "MOY×COEF"
            isTermTpl -> "AV×COEF"
            else -> "TOT"
        }
        headers += extras
        headers += when {
            isTermTpl && lang == Language.FR -> "APPRÉC."
            isTermTpl -> "REMARK"
            lang == Language.FR -> "APP."
            else -> "REM."
        }

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
        val isTerm = card.period.type == ReportPeriodType.TERM || template.periodType == ReportPeriodType.TERM
        return if (isTerm) {
            termResultsBand(card, template, bold, regular, small, muted, avgFont, lang)
        } else {
            annualResultsBand(card, template, bold, regular, small, muted, avgFont, lang)
        }
    }

    /** Trimestre : TERM RESULT · CLASS PROFILE · MAKE EFFORT IN (maquette). */
    private fun termResultsBand(
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
        table.setWidths(floatArrayOf(1.15f, 1f, 0.95f))
        val o = card.overall
        val maxPoints = o.totalCoefficient.multiply(BigDecimal("20"))

        val result = PdfPTable(1)
        result.widthPercentage = 100f
        result.addCell(
            dottedStatRow(
                if (lang == Language.FR) "Total points" else "Total points",
                "${o.totalPoints?.let { fmt(it) } ?: "—"} / ${fmt(maxPoints)}",
                regular,
                bold,
            )
        )
        val avgRow = PdfPTable(floatArrayOf(1.1f, 1f))
        avgRow.widthPercentage = 100f
        val avgL = PdfPCell(Phrase(if (lang == Language.FR) "Moyenne du trimestre" else "Term average", regular))
        avgL.border = Rectangle.BOTTOM
        avgL.borderColor = LINE
        avgL.borderWidthBottom = 0.6f
        avgL.setPaddingTop(2f)
        avgL.setPaddingBottom(3f)
        avgRow.addCell(avgL)
        val avgV = PdfPCell(Phrase(o.average?.let { "${fmt(it)} /20" } ?: "—", avgFont))
        avgV.border = Rectangle.BOTTOM
        avgV.borderColor = LINE
        avgV.borderWidthBottom = 0.6f
        avgV.horizontalAlignment = Element.ALIGN_RIGHT
        avgV.setPaddingTop(2f)
        avgV.setPaddingBottom(3f)
        avgRow.addCell(avgV)
        result.addCell(borderlessCell(avgRow))
        val rankText = if (o.rankStatus.name == "RANKED" && o.rank != null) {
            "${o.rank} / ${o.rankedCount}"
        } else "—"
        result.addCell(
            dottedStatRow(
                if (lang == Language.FR) "Rang" else "Position",
                rankText,
                regular,
                bold,
            )
        )
        val mention = (o.mentionLabel ?: o.mentionCode ?: "").uppercase()
        if (mention.isNotBlank()) {
            val mentionRow = PdfPTable(floatArrayOf(0.7f, 1.4f))
            mentionRow.widthPercentage = 100f
            val pill = PdfPCell(
                Phrase(mention, FontFactory.getFont(FontFactory.HELVETICA_BOLD, small.size + 0.4f, Color.WHITE))
            )
            pill.backgroundColor = AMBER
            pill.border = Rectangle.NO_BORDER
            pill.horizontalAlignment = Element.ALIGN_CENTER
            pill.setPaddingTop(3f)
            pill.setPaddingBottom(3f)
            mentionRow.addCell(pill)
            val passed = if (o.subjectsPassed != null) {
                if (lang == Language.FR) "Admis en ${o.subjectsPassed} / ${o.subjectsCounted} matières"
                else "Passed in ${o.subjectsPassed} / ${o.subjectsCounted} subjects"
            } else ""
            val passedCell = PdfPCell(Phrase(passed, small))
            passedCell.border = Rectangle.NO_BORDER
            passedCell.verticalAlignment = Element.ALIGN_MIDDLE
            passedCell.setPaddingLeft(4f)
            mentionRow.addCell(passedCell)
            val wrap = PdfPCell(mentionRow)
            wrap.border = Rectangle.NO_BORDER
            wrap.setPaddingTop(4f)
            result.addCell(wrap)
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "RÉSULTAT DU TRIMESTRE" else "TERM RESULT",
                result,
            )
        )

        val profile = PdfPTable(1)
        profile.widthPercentage = 100f
        val p = card.classProfile
        if (template.showClassProfile && p != null) {
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Moyenne ≥ ${fmt(p.threshold, 0)}" else "Average ≥ ${fmt(p.threshold, 0)}",
                    p.countAtOrAboveThreshold.toString(),
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Moyenne de la classe" else "Class average",
                    p.classAverage?.let { fmt(it) } ?: "—",
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "% de réussite" else "Success rate",
                    p.successRate?.let { "${fmt(it)} %" } ?: "—",
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Max · min" else "Max · min",
                    "${p.highestAverage?.let { fmt(it) } ?: "—"} · ${p.lowestAverage?.let { fmt(it) } ?: "—"}",
                    muted,
                    bold,
                )
            )
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "PROFIL DE LA CLASSE" else "CLASS PROFILE",
                profile,
            )
        )

        val effort = PdfPTable(1)
        effort.widthPercentage = 100f
        if (template.showEffortSubjects && o.effortSubjectNames.isNotEmpty()) {
            val names = PdfPCell(
                Phrase(
                    o.effortSubjectNames.joinToString("\n") { it.uppercase() },
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, avgFont.size - 3f, INK),
                )
            )
            names.border = Rectangle.NO_BORDER
            effort.addCell(names)
            val note = PdfPCell(
                Phrase(
                    if (lang == Language.FR) {
                        "(matières avec moyenne < 10 · seuil = paramètre, Q-28)"
                    } else {
                        "(subjects with average below 10 · threshold = parameter, Q-28)"
                    },
                    FontFactory.getFont(FontFactory.HELVETICA, muted.size - 0.4f, MUTED),
                )
            )
            note.border = Rectangle.NO_BORDER
            note.setPaddingTop(3f)
            effort.addCell(note)
        } else {
            val empty = PdfPCell(Phrase("—", muted))
            empty.border = Rectangle.NO_BORDER
            effort.addCell(empty)
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "À RENFORCER" else "MAKE EFFORT IN",
                effort,
            )
        )
        return table
    }

    /** Annuel : résultat · rappels TRIM · profil. */
    private fun annualResultsBand(
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
        table.setWidths(floatArrayOf(1f, 1f, 1.1f))
        val o = card.overall

        val resultInner = PdfPTable(1)
        resultInner.widthPercentage = 100f
        val avgRow = PdfPTable(floatArrayOf(1.2f, 1f))
        avgRow.widthPercentage = 100f
        val avgLabel = PdfPCell(
            Phrase(if (lang == Language.FR) "Moyenne de l'élève" else "Student average", regular)
        )
        avgLabel.border = Rectangle.BOTTOM
        avgLabel.borderColor = LINE
        avgLabel.borderWidthBottom = 0.6f
        avgLabel.verticalAlignment = Element.ALIGN_BOTTOM
        avgLabel.setPaddingBottom(3f)
        avgRow.addCell(avgLabel)
        val avgValue = PdfPCell(Phrase(o.average?.let { "${fmt(it)} /20" } ?: "—", avgFont))
        avgValue.border = Rectangle.BOTTOM
        avgValue.borderColor = LINE
        avgValue.borderWidthBottom = 0.6f
        avgValue.horizontalAlignment = Element.ALIGN_RIGHT
        avgValue.verticalAlignment = Element.ALIGN_BOTTOM
        avgValue.setPaddingBottom(3f)
        avgRow.addCell(avgValue)
        resultInner.addCell(borderlessCell(avgRow))

        val rankRow = PdfPTable(floatArrayOf(1f, 1f))
        rankRow.widthPercentage = 100f
        val rankLabel = PdfPCell(Phrase(if (lang == Language.FR) "Rang" else "Rank", regular))
        rankLabel.border = Rectangle.BOTTOM
        rankLabel.borderColor = LINE
        rankLabel.borderWidthBottom = 0.6f
        rankLabel.setPaddingTop(3f)
        rankLabel.setPaddingBottom(3f)
        rankRow.addCell(rankLabel)
        val rankText = if (o.rankStatus.name == "RANKED" && o.rank != null) {
            "${o.rank} / ${o.rankedCount}"
        } else "—"
        val rankValue = PdfPCell(Phrase(rankText, bold))
        rankValue.border = Rectangle.BOTTOM
        rankValue.borderColor = LINE
        rankValue.borderWidthBottom = 0.6f
        rankValue.horizontalAlignment = Element.ALIGN_RIGHT
        rankValue.setPaddingTop(3f)
        rankValue.setPaddingBottom(3f)
        rankRow.addCell(rankValue)
        resultInner.addCell(borderlessCell(rankRow))

        val mention = (o.mentionLabel ?: o.mentionCode ?: "").uppercase()
        if (mention.isNotBlank()) {
            val pillTable = PdfPTable(floatArrayOf(1.15f, 1.85f))
            pillTable.widthPercentage = 100f
            val pill = PdfPCell(
                Phrase(mention, FontFactory.getFont(FontFactory.HELVETICA_BOLD, small.size + 0.5f, Color.WHITE))
            )
            pill.backgroundColor = AMBER
            pill.border = Rectangle.NO_BORDER
            pill.horizontalAlignment = Element.ALIGN_CENTER
            pill.setPaddingTop(3.5f)
            pill.setPaddingBottom(3.5f)
            pill.setPaddingLeft(8f)
            pill.setPaddingRight(8f)
            pillTable.addCell(pill)
            val spacer = PdfPCell(Phrase(""))
            spacer.border = Rectangle.NO_BORDER
            pillTable.addCell(spacer)
            val pillWrap = PdfPCell(pillTable)
            pillWrap.border = Rectangle.NO_BORDER
            pillWrap.setPaddingTop(5f)
            resultInner.addCell(pillWrap)
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "RÉSULTAT ANNUEL" else "ANNUAL RESULT",
                resultInner,
            )
        )

        val recap = PdfPTable(floatArrayOf(1.35f, 1f, 1f))
        recap.widthPercentage = 100f
        fun headCell(text: String): PdfPCell {
            val c = PdfPCell(Phrase(text, bold))
            c.borderColor = LINE
            c.backgroundColor = Color.WHITE
            c.horizontalAlignment = Element.ALIGN_CENTER
            c.verticalAlignment = Element.ALIGN_MIDDLE
            c.setPadding(3f)
            return c
        }
        recap.addCell(headCell(""))
        recap.addCell(headCell("MOY."))
        recap.addCell(headCell(if (lang == Language.FR) "RANG" else "RANK"))
        card.termRecaps.forEach { t ->
            val label = if (lang == Language.FR) "TRIM ${t.termNumber}" else "TERM ${t.termNumber}"
            val labelCell = PdfPCell(Phrase(label, bold))
            labelCell.borderColor = LINE
            labelCell.backgroundColor = TINT
            labelCell.horizontalAlignment = Element.ALIGN_CENTER
            labelCell.verticalAlignment = Element.ALIGN_MIDDLE
            labelCell.setPadding(3f)
            recap.addCell(labelCell)
            fun dataCell(text: String): PdfPCell {
                val c = PdfPCell(Phrase(text, regular))
                c.borderColor = LINE
                c.backgroundColor = Color.WHITE
                c.horizontalAlignment = Element.ALIGN_CENTER
                c.verticalAlignment = Element.ALIGN_MIDDLE
                c.setPadding(3f)
                return c
            }
            recap.addCell(dataCell(t.average?.let { fmt(it) } ?: "—"))
            recap.addCell(dataCell(t.rank?.toString() ?: "—"))
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "RAPPELS DES MOYENNES" else "TERM AVERAGES",
                recap,
            )
        )

        val profile = PdfPTable(1)
        profile.widthPercentage = 100f
        val p = card.classProfile
        if (template.showClassProfile && p != null) {
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Élèves avec moyenne ≥ ${fmt(p.threshold, 0)}"
                    else "Pupils with average ≥ ${fmt(p.threshold, 0)}",
                    p.countAtOrAboveThreshold.toString(),
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Moyenne de la classe" else "Class average",
                    p.classAverage?.let { fmt(it) } ?: "—",
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "% de réussite" else "Pass rate",
                    p.successRate?.let { "${fmt(it)} %" } ?: "—",
                    muted,
                    bold,
                )
            )
            profile.addCell(
                dottedStatRow(
                    if (lang == Language.FR) "Moyenne max - min" else "Max - min average",
                    "${p.highestAverage?.let { fmt(it) } ?: "—"} - ${p.lowestAverage?.let { fmt(it) } ?: "—"}",
                    muted,
                    bold,
                )
            )
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "PROFIL DE LA CLASSE" else "CLASS PROFILE",
                profile,
            )
        )
        return table
    }

    private fun dottedStatRow(label: String, value: String, labelFont: Font, valueFont: Font): PdfPCell {
        val row = PdfPTable(floatArrayOf(2.2f, 1f))
        row.widthPercentage = 100f
        val l = PdfPCell(Phrase(label, labelFont))
        l.border = Rectangle.BOTTOM
        l.borderColor = LINE
        l.borderWidthBottom = 0.5f
        l.setPaddingTop(2f)
        l.setPaddingBottom(2f)
        row.addCell(l)
        val v = PdfPCell(Phrase(value, valueFont))
        v.border = Rectangle.BOTTOM
        v.borderColor = LINE
        v.borderWidthBottom = 0.5f
        v.horizontalAlignment = Element.ALIGN_RIGHT
        v.setPaddingTop(2f)
        v.setPaddingBottom(2f)
        row.addCell(v)
        return borderlessCell(row)
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
            add(
                dot(
                    if (lang == Language.FR) "Tableau d'honneur" else "Honour roll",
                    distinctionLabel(rev?.distinction, lang),
                    muted,
                    bold,
                )
            )
            add(
                dot(
                    if (lang == Language.FR) "T.H. + encouragements — félicitations"
                    else "H.R. + encouragement — congratulation",
                    "—",
                    muted,
                    bold,
                )
            )
            add(
                dot(
                    if (lang == Language.FR) "Avertissement — blâme travail" else "Academic warning",
                    sanctionLabel(rev?.workSanction, lang),
                    muted,
                    bold,
                )
            )
        }
        val conduct = Paragraph().apply {
            leading = small.size + 2f
            val abs = "${rev?.absenceHoursTotal ?: 0} · ${rev?.absenceHoursUnjustified ?: 0}"
            add(
                dot(
                    if (lang == Language.FR) "Absences (h) — non justifiées" else "Absences (h) — unjustified",
                    abs,
                    muted,
                    bold,
                )
            )
            add(
                dot(
                    if (lang == Language.FR) "Retards / Absentéisme" else "Absenteeism",
                    "${rev?.lateCount ?: 0}",
                    muted,
                    bold,
                )
            )
            add(
                dot(
                    if (lang == Language.FR) "Avertissement — blâme conduite" else "Warning — blame",
                    sanctionLabel(rev?.conductSanction, lang),
                    muted,
                    bold,
                )
            )
        }
        table.addCell(
            framed(
                if (lang == Language.FR) "TRAVAIL DE L'ÉLÈVE" else "WORK",
                work,
                bold,
                muted,
            )
        )
        table.addCell(
            framed(
                if (lang == Language.FR) "CONDUITE DE L'ÉLÈVE" else "CONDUCT",
                conduct,
                bold,
                muted,
            )
        )
        return table
    }

    private fun distinctionLabel(level: DistinctionLevel?, lang: Language): String =
        when (level) {
            null, DistinctionLevel.NONE -> if (lang == Language.FR) "Non" else "No"
            DistinctionLevel.HONOR_ROLL -> if (lang == Language.FR) "Oui" else "Yes"
            DistinctionLevel.HONOR_ROLL_ENCOURAGEMENT ->
                if (lang == Language.FR) "T.H. + Encouragement" else "H.R. + Encouragement"
            DistinctionLevel.HONOR_ROLL_CONGRATULATIONS ->
                if (lang == Language.FR) "T.H. + Félicitations" else "H.R. + Congratulations"
        }

    private fun sanctionLabel(level: SanctionLevel?, lang: Language): String =
        when (level) {
            null, SanctionLevel.NONE -> "—"
            SanctionLevel.WARNING -> if (lang == Language.FR) "Avertissement" else "Warning"
            SanctionLevel.BLAME -> if (lang == Language.FR) "Blâme" else "Blame"
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
        table.addCell(visa(if (lang == Language.FR) "VISA DU PARENT" else "PARENT'S SIGNATURE"))
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
        val wrap = PdfPTable(1)
        wrap.widthPercentage = 100f
        val rule = PdfPCell(Phrase(" "))
        rule.border = Rectangle.TOP
        rule.borderColor = INK
        rule.borderWidthTop = 0.8f
        rule.fixedHeight = 4f
        wrap.addCell(rule)

        val table = PdfPTable(floatArrayOf(1.35f, 1.1f, 1f))
        table.widthPercentage = 100f

        val kobeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, muted.size + 0.3f, AMBER)
        val leftPara = Paragraph().apply {
            leading = muted.size + 1.4f
            add(Chunk(if (lang == Language.FR) "Développé par " else "Developed by ", muted))
            add(Chunk("KOBE Corporation\n", kobeFont))
            val stamped = card.generatedAt.atZone(ZoneId.of("Africa/Douala"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"))
            val stampedEn = card.generatedAt.atZone(ZoneId.of("Africa/Douala"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm"))
            val isTerm = card.period.type == ReportPeriodType.TERM
            val meta = when {
                lang == Language.FR ->
                    "Bulletin délivré sans rature ni surcharge · Imprimé le $stamped"
                isTerm ->
                    "Issued without erasure or alteration · Printed on $stampedEn"
                else ->
                    "Report card issued without alteration · Printed on $stampedEn"
            }
            add(Chunk(meta, FontFactory.getFont(FontFactory.HELVETICA, muted.size - 0.3f, MUTED)))
        }
        val left = PdfPCell()
        left.border = Rectangle.NO_BORDER
        left.verticalAlignment = Element.ALIGN_BOTTOM
        left.addElement(leftPara)
        table.addCell(left)

        val legendText = when (lang) {
            Language.FR -> template.legend?.fr
            Language.EN -> template.legend?.en
        }.orEmpty().ifBlank { "A ≥ 16 · B+ ≥ 14 · B ≥ 12 · C < 12" }
        val center = Paragraph().apply {
            alignment = Element.ALIGN_CENTER
            leading = muted.size + 1.2f
            add(legendPhrase(legendText, muted.size))
        }
        val centerCell = PdfPCell()
        centerCell.border = Rectangle.NO_BORDER
        centerCell.verticalAlignment = Element.ALIGN_MIDDLE
        centerCell.addElement(center)
        table.addCell(centerCell)

        val barcode = Barcode128()
        barcode.code = card.verificationCode.filter { it.isDigit() }.take(13).ifBlank { card.verificationCode }
        barcode.barHeight = 16f
        barcode.x = 0.7f
        val img = barcode.createImageWithBarcode(writer.directContent, INK, INK)
        img.scaleAbsolute(118f, 24f)
        val right = Paragraph().apply {
            alignment = Element.ALIGN_RIGHT
            leading = muted.size + 1.1f
            add(Chunk(img, 0f, 0f))
            add(Chunk("\n${formatVerificationCode(card.verificationCode)}\n", muted))
            add(
                Chunk(
                    if (lang == Language.FR) "Vérification : exemple.cm/verif"
                    else "Check : example.cm/verify",
                    FontFactory.getFont(FontFactory.HELVETICA, muted.size - 0.3f, MUTED),
                )
            )
        }
        val rightCell = PdfPCell()
        rightCell.border = Rectangle.NO_BORDER
        rightCell.horizontalAlignment = Element.ALIGN_RIGHT
        rightCell.verticalAlignment = Element.ALIGN_BOTTOM
        rightCell.addElement(right)
        table.addCell(rightCell)

        val body = PdfPCell(table)
        body.border = Rectangle.NO_BORDER
        body.setPadding(0f)
        wrap.addCell(body)
        return wrap
    }

    private fun legendPhrase(text: String, size: Float): Phrase {
        val phrase = Phrase()
        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, INK)
        val regular = FontFactory.getFont(FontFactory.HELVETICA, size, INK)
        val regex = Regex("(A\\+|A|B\\+|B|C|D|E)")
        var last = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > last) {
                phrase.add(Chunk(text.substring(last, match.range.first), regular))
            }
            phrase.add(Chunk(match.value, bold))
            last = match.range.last + 1
        }
        if (last < text.length) phrase.add(Chunk(text.substring(last), regular))
        return phrase
    }

    private fun formatVerificationCode(code: String): String {
        val digits = code.filter { it.isDigit() }.padStart(13, '0').take(13)
        return "${digits.substring(0, 4)} ${digits.substring(4, 8)} ${digits.substring(8, 12)} ${digits.substring(12)}"
    }

    private fun framed(title: String, body: Paragraph, titleFont: Font, muted: Font): PdfPCell {
        val wrap = PdfPTable(1)
        wrap.widthPercentage = 100f
        val bodyCell = PdfPCell()
        bodyCell.border = Rectangle.NO_BORDER
        bodyCell.setPadding(0f)
        bodyCell.addElement(body)
        wrap.addCell(bodyCell)
        return framed(title, wrap)
    }

    private fun framed(title: String, body: PdfPTable): PdfPCell {
        val inner = PdfPTable(1)
        inner.widthPercentage = 100f
        val head = PdfPCell(
            Phrase(title.uppercase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6f, INK))
        )
        head.backgroundColor = TINT
        head.borderColor = LINE
        head.setPadding(3f)
        inner.addCell(head)
        val content = PdfPCell(body)
        content.borderColor = LINE
        content.setPadding(5f)
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
