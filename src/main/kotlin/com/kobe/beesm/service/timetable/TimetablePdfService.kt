package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.academic.SchoolBulletinBrandDto
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.shared.ContactInfo
import com.kobe.beesm.model.shared.Language
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.timetable.EntryKind
import com.kobe.beesm.model.timetable.GridPeriod
import com.kobe.beesm.model.timetable.PeriodKind
import com.kobe.beesm.model.timetable.TimeSlotGrid
import com.kobe.beesm.model.timetable.TimetableEntry
import com.kobe.beesm.repository.timetable.TimeSlotGridRepository
import com.kobe.beesm.repository.timetable.TimetableEntryRepository
import com.kobe.beesm.repository.timetable.TimetableRepository
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.service.academic.api.SchoolClassApi
import com.kobe.beesm.service.fees.api.FeesApi
import com.kobe.beesm.shared.security.CurrentUser
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PDF emploi du temps A4 paysage — **une page par classe** (BR-62).
 * En-tête officiel + [Timetable.headerContact] (Q-45) ; signature Administration.
 */
@Service
class TimetablePdfService(
    private val timetableRepository: TimetableRepository,
    private val entryRepository: TimetableEntryRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val schoolBrandApi: SchoolBrandApi,
    private val schoolClassApi: SchoolClassApi,
    private val academicYearApi: AcademicYearApi,
    private val feesApi: FeesApi
) {
    fun renderClassPdf(
        currentUser: CurrentUser,
        timetableId: String,
        classId: String,
        languageOverride: Language? = null,
        schoolIdOverride: String? = null,
        forStudentId: String? = null
    ): Mono<ByteArray> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return timetableRepository.findByIdAndSchoolId(timetableId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { timetable ->
                assertPortalFeesAccess(currentUser, schoolId, timetable.academicYearId, forStudentId)
                    .then(
                Mono.zip(
                    gridRepository.findByIdAndSchoolId(timetable.gridId, schoolId)
                        .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found"))),
                    entryRepository.findBySchoolIdAndTimetableIdAndClassId(schoolId, timetableId, classId)
                        .collectList(),
                    schoolClassApi.getSummary(schoolId, classId),
                    schoolBrandApi.getBulletinBrand(schoolId),
                    academicYearApi.getSummary(schoolId, timetable.academicYearId)
                ).map { tuple ->
                    val grid = tuple.t1
                    val entries = tuple.t2
                    val clazz = tuple.t3
                    val brand = tuple.t4
                    val year = tuple.t5
                    val lang = languageOverride ?: clazz.defaultLanguage
                    render(
                        TimetablePdfModel(
                            brand = brand,
                            yearLabel = year.label,
                            className = clazz.name,
                            language = lang,
                            headerContact = timetable.headerContact,
                            grid = grid,
                            entries = entries,
                            note = timetable.note
                        )
                    )
                }
                    )
            }
    }

    private fun assertPortalFeesAccess(
        currentUser: CurrentUser,
        schoolId: String,
        academicYearId: String,
        forStudentId: String?
    ): Mono<Void> {
        if (!currentUser.hasAnyRole(Role.PARENT, Role.STUDENT)) {
            return Mono.empty()
        }
        val studentId =
            forStudentId?.trim()?.takeIf { it.isNotEmpty() }
                ?: currentUser.profileId?.takeIf {
                    currentUser.hasRole(Role.STUDENT) &&
                        currentUser.profileType == ProfileType.STUDENT &&
                        !it.isNullOrBlank()
                }
        if (studentId.isNullOrBlank()) {
            return Mono.error(
                ForbiddenException(
                    "FEES_STUDENT_REQUIRED",
                    "Student id is required to open the timetable PDF when fees soft-block applies"
                )
            )
        }
        return feesApi.assertCanViewAcademics(currentUser, schoolId, academicYearId, studentId)
    }

    /** Rendu pur (testable) — refuse toute sortie > 1 page. */
    fun render(model: TimetablePdfModel): ByteArray {
        var fontSize = MAX_FONT
        var lastError: String? = null
        while (fontSize >= MIN_FONT - 0.01f) {
            val bytes = build(model, fontSize)
            val pages = pageCount(bytes)
            if (pages == 1) return bytes
            lastError = "Content spans $pages pages at ${"%.1f".format(fontSize)}pt"
            fontSize -= 0.25f
        }
        throw BusinessRuleException(
            "TIMETABLE_PDF_OVERFLOW",
            "Timetable does not fit on one A4 landscape page even at minimum font size (${MIN_FONT}pt). $lastError"
        )
    }

    fun pageCount(pdfBytes: ByteArray): Int = PdfReader(pdfBytes).use { it.numberOfPages }

    private fun build(model: TimetablePdfModel, fontSize: Float): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate(), 16f, 16f, 12f, 12f)
        PdfWriter.getInstance(document, out)
        document.open()

        val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize)
        val regular = FontFactory.getFont(FontFactory.HELVETICA, fontSize)
        val small = FontFactory.getFont(FontFactory.HELVETICA, (fontSize - 0.8f).coerceAtLeast(4.5f))
        val smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, (fontSize - 0.5f).coerceAtLeast(4.8f))
        val tiny = FontFactory.getFont(FontFactory.HELVETICA, (fontSize - 1.2f).coerceAtLeast(4.2f))

        addHeader(document, model, bold, regular, small)
        document.add(Paragraph(" ", tiny))
        document.add(buildGridTable(model, smallBold, tiny))
        addSaturdayBlock(document, model, smallBold, tiny)
        document.add(Paragraph(" ", tiny))
        addFooter(document, model, small)

        document.close()
        return out.toByteArray()
    }

    private fun addHeader(
        document: Document,
        model: TimetablePdfModel,
        bold: Font,
        regular: Font,
        small: Font
    ) {
        val lang = model.language
        val brand = model.brand
        fun pick(fr: String?, en: String?) =
            if (lang == Language.FR) fr?.takeIf { it.isNotBlank() } ?: en
            else en?.takeIf { it.isNotBlank() } ?: fr

        val line1 = listOfNotNull(
            pick(brand.republicFr, brand.republicEn),
            pick(brand.mottoFr, brand.mottoEn)?.let { "— $it" }
        ).joinToString(" ")
        if (line1.isNotBlank()) {
            document.add(Paragraph(line1, small).also { it.alignment = Element.ALIGN_CENTER })
        }
        pick(brand.ministryFr, brand.ministryEn)?.let {
            document.add(Paragraph(it, small).also { p -> p.alignment = Element.ALIGN_CENTER })
        }
        val deleg = listOfNotNull(
            pick(brand.regionalDelegationFr, brand.regionalDelegationEn),
            pick(brand.divisionalDelegationFr, brand.divisionalDelegationEn)
        ).joinToString(" · ")
        if (deleg.isNotBlank()) {
            document.add(Paragraph(deleg, small).also { it.alignment = Element.ALIGN_CENTER })
        }

        val schoolName = if (lang == Language.FR) brand.nameFr else brand.nameEn
        document.add(
            Paragraph(schoolName, bold).also {
                it.alignment = Element.ALIGN_CENTER
                it.spacingBefore = 2f
            }
        )

        val contact = model.headerContact
        val contactBits = buildList {
            contact?.phones?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?.let { add(it.joinToString(" / ")) }
            contact?.email?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (isEmpty()) {
                brand.phones.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                    ?.let { add(it.joinToString(" / ")) }
            }
            brand.postalBox?.takeIf { it.isNotBlank() }?.let { add("BP $it") }
        }
        if (contactBits.isNotEmpty()) {
            document.add(
                Paragraph(contactBits.joinToString(" · "), small)
                    .also { it.alignment = Element.ALIGN_CENTER }
            )
        }

        val title = when (lang) {
            Language.FR -> "EMPLOI DU TEMPS — ${model.yearLabel}"
            Language.EN -> "TIMETABLE — ${model.yearLabel}"
        }
        document.add(
            Paragraph(title, bold).also {
                it.alignment = Element.ALIGN_CENTER
                it.spacingBefore = 4f
            }
        )
        document.add(
            Paragraph(model.className, bold).also {
                it.alignment = Element.ALIGN_CENTER
                it.spacingAfter = 2f
            }
        )
        model.note?.takeIf { it.isNotBlank() }?.let {
            document.add(Paragraph(it, regular).also { p -> p.alignment = Element.ALIGN_CENTER })
        }
    }

    private fun buildGridTable(model: TimetablePdfModel, headerFont: Font, cellFont: Font): PdfPTable {
        val days = model.grid.days
            .filter { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }
            .sortedBy { it.value }
            .ifEmpty {
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                )
            }
        val colCount = 1 + days.size
        val widths = FloatArray(colCount) { if (it == 0) 1.35f else 1f }
        val table = PdfPTable(widths)
        table.widthPercentage = 100f
        table.headerRows = 1

        addCell(table, timeHeader(model.language), headerFont, Element.ALIGN_CENTER, HEADER_BG, true)
        days.forEach { day ->
            addCell(table, dayLabel(day, model.language), headerFont, Element.ALIGN_CENTER, HEADER_BG, true)
        }

        for (period in model.grid.periods) {
            when (period.kind) {
                PeriodKind.LESSON -> {
                    addCell(table, periodLabel(period), cellFont, Element.ALIGN_CENTER, TIME_BG, true)
                    days.forEach { day ->
                        val text = cellText(model.entries, day, period.number!!)
                        addCell(table, text, cellFont, Element.ALIGN_CENTER, Color.WHITE, false)
                    }
                }
                PeriodKind.BREAK, PeriodKind.CLEANUP, PeriodKind.CLOSING, PeriodKind.ASSEMBLY -> {
                    addCell(table, periodLabel(period), cellFont, Element.ALIGN_CENTER, BAND_BG, true)
                    val band = bandLabel(period, model.language)
                    val bandCell = PdfPCell(Phrase(band, cellFont)).apply {
                        colspan = days.size
                        horizontalAlignment = Element.ALIGN_CENTER
                        verticalAlignment = Element.ALIGN_MIDDLE
                        backgroundColor = BAND_BG
                        setPadding(2f)
                    }
                    table.addCell(bandCell)
                }
            }
        }
        return table
    }

    private fun cellText(entries: List<TimetableEntry>, day: DayOfWeek, periodNumber: Int): String {
        val covering = entries.filter { entry ->
            entry.day == day &&
                entry.startPeriod != null &&
                periodNumber >= entry.startPeriod!! &&
                periodNumber < entry.startPeriod!! + entry.periodCount.coerceAtLeast(1)
        }
        if (covering.isEmpty()) return ""
        return covering
            .sortedWith(compareBy({ it.optionCode }, { it.id }))
            .joinToString(" / ") { entry ->
                val subject = entry.subjectLabel
                    ?: entry.label?.let { resolveLocalized(it, Language.FR) }
                    ?: entry.activityType?.name
                    ?: if (entry.kind == EntryKind.ACTIVITY) "—" else ""
                val teacher = entry.teacherLabel?.takeIf { it.isNotBlank() }
                val option = entry.optionCode.takeIf { it.isNotBlank() }
                buildString {
                    if (option != null) append("$option ")
                    append(subject)
                    if (teacher != null) append("\n").append(teacher)
                }.trim()
            }
    }

    private fun addSaturdayBlock(
        document: Document,
        model: TimetablePdfModel,
        headerFont: Font,
        cellFont: Font
    ) {
        val saturday = model.entries
            .filter { it.day == DayOfWeek.SATURDAY }
            .sortedWith(compareBy({ it.customStart ?: LocalTime.MIN }, { it.slotKey }))
        if (saturday.isEmpty()) return
        val title = when (model.language) {
            Language.FR -> "Samedi / hors grille"
            Language.EN -> "Saturday / off-grid"
        }
        document.add(Paragraph(title, headerFont).also { it.spacingBefore = 4f })
        saturday.forEach { entry ->
            val time = when {
                entry.customStart != null && entry.customEnd != null ->
                    "${fmtTime(entry.customStart!!)} – ${fmtTime(entry.customEnd!!)}"
                else -> entry.slotKey
            }
            val subject = entry.subjectLabel
                ?: entry.label?.let { resolveLocalized(it, model.language) }
                ?: entry.activityType?.name
                ?: "—"
            val teacher = entry.teacherLabel?.let { " ($it)" } ?: ""
            document.add(Paragraph("$time  $subject$teacher", cellFont))
        }
    }

    private fun addFooter(document: Document, model: TimetablePdfModel, font: Font) {
        val label = when (model.language) {
            Language.FR -> "Administration"
            Language.EN -> "Administration"
        }
        document.add(
            Paragraph("$label : ____________________________", font).also {
                it.alignment = Element.ALIGN_RIGHT
                it.spacingBefore = 8f
            }
        )
    }

    private fun periodLabel(period: GridPeriod): String {
        val time = "${fmtTime(period.start)} – ${fmtTime(period.end)}"
        return if (period.number != null) "${period.number}\n$time" else time
    }

    private fun bandLabel(period: GridPeriod, language: Language): String {
        val fromLabel = period.label?.let { resolveLocalized(it, language) }
        if (!fromLabel.isNullOrBlank()) return fromLabel
        return when (period.kind) {
            PeriodKind.BREAK -> if (language == Language.FR) "PAUSE" else "BREAK"
            PeriodKind.CLEANUP -> if (language == Language.FR) "NETTOYAGE GÉNÉRAL" else "GENERAL CLEAN UP"
            PeriodKind.CLOSING -> if (language == Language.FR) "RENTRÉE" else "CLOSING"
            PeriodKind.ASSEMBLY -> if (language == Language.FR) "ASSEMBLÉE" else "ASSEMBLY"
            PeriodKind.LESSON -> ""
        }
    }

    private fun dayLabel(day: DayOfWeek, language: Language): String = when (language) {
        Language.FR -> when (day) {
            DayOfWeek.MONDAY -> "Lundi"
            DayOfWeek.TUESDAY -> "Mardi"
            DayOfWeek.WEDNESDAY -> "Mercredi"
            DayOfWeek.THURSDAY -> "Jeudi"
            DayOfWeek.FRIDAY -> "Vendredi"
            DayOfWeek.SATURDAY -> "Samedi"
            DayOfWeek.SUNDAY -> "Dimanche"
        }
        Language.EN -> day.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun timeHeader(language: Language): String =
        if (language == Language.FR) "Horaire" else "Time"

    private fun resolveLocalized(text: LocalizedText, language: Language): String? =
        text.resolve(language, fallback = true)

    private fun fmtTime(time: LocalTime): String = time.format(TIME_FMT)

    private fun addCell(
        table: PdfPTable,
        text: String,
        font: Font,
        align: Int,
        bg: Color,
        boldPad: Boolean
    ) {
        val cell = PdfPCell(Phrase(text, font)).apply {
            horizontalAlignment = align
            verticalAlignment = Element.ALIGN_MIDDLE
            backgroundColor = bg
            setPadding(if (boldPad) 3f else 2f)
        }
        table.addCell(cell)
    }

    data class TimetablePdfModel(
        val brand: SchoolBulletinBrandDto,
        val yearLabel: String,
        val className: String,
        val language: Language,
        val headerContact: ContactInfo?,
        val grid: TimeSlotGrid,
        val entries: List<TimetableEntry>,
        val note: String? = null
    )

    companion object {
        private const val MAX_FONT = 7.5f
        private const val MIN_FONT = 4.5f
        private val TIME_FMT = DateTimeFormatter.ofPattern("H:mm")
        private val HEADER_BG = Color(230, 230, 230)
        private val TIME_BG = Color(245, 245, 245)
        private val BAND_BG = Color(235, 240, 245)
    }
}
