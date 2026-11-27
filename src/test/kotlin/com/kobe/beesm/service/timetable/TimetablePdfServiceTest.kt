package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.academic.SchoolBulletinBrandDto
import com.kobe.beesm.model.shared.ContactInfo
import com.kobe.beesm.model.shared.Language
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.timetable.EntryKind
import com.kobe.beesm.model.timetable.GridPeriod
import com.kobe.beesm.model.timetable.PeriodKind
import com.kobe.beesm.model.timetable.TimeSlotGrid
import com.kobe.beesm.model.timetable.TimetableEntry
import org.mockito.kotlin.mock
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** BR-62 : PDF EDT A4 paysage = exactement 1 page par classe. */
class TimetablePdfServiceTest {

    private val pdf = TimetablePdfService(
        timetableRepository = mock(),
        entryRepository = mock(),
        gridRepository = mock(),
        schoolBrandApi = mock(),
        schoolClassApi = mock(),
        academicYearApi = mock(),
        feesApi = mock()
    )

    @Test
    fun `full week 40 lesson cells fits on exactly one A4 landscape page FR`() {
        val model = sampleModel(Language.FR, fillAll = true)
        val bytes = pdf.render(model)
        assertEquals(1, pdf.pageCount(bytes))
        assertTrue(bytes.size > 500)
    }

    @Test
    fun `full week fits on one page EN with saturday block`() {
        val model = sampleModel(Language.EN, fillAll = true, withSaturday = true)
        val bytes = pdf.render(model)
        assertEquals(1, pdf.pageCount(bytes))
    }

    @Test
    fun `LV2 parallel options render without second page`() {
        val base = sampleModel(Language.FR, fillAll = false)
        val mondayP2 = listOf(
            entry("c1", DayOfWeek.MONDAY, 2, "ALL", "Prof A", optionGroup = "LV2", optionCode = "ALL"),
            entry("c1", DayOfWeek.MONDAY, 2, "ESP", "Prof B", optionGroup = "LV2", optionCode = "ESP")
        )
        val bytes = pdf.render(base.copy(entries = mondayP2))
        assertEquals(1, pdf.pageCount(bytes))
    }

    private fun sampleModel(
        language: Language,
        fillAll: Boolean,
        withSaturday: Boolean = false
    ): TimetablePdfService.TimetablePdfModel {
        val days = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
        val grid = TimeSlotGrid(
            id = "grid-1",
            schoolId = "school-1",
            academicYearId = "year-1",
            name = "Standard",
            days = days,
            periods = fixturePeriods()
        )
        val entries = mutableListOf<TimetableEntry>()
        if (fillAll) {
            for (day in days) {
                for (p in 1..8) {
                    entries += entry("c1", day, p, "MAT$p", "T$p")
                }
            }
        }
        if (withSaturday) {
            entries += TimetableEntry(
                schoolId = "school-1",
                academicYearId = "year-1",
                timetableId = "tt-1",
                classId = "c1",
                day = DayOfWeek.SATURDAY,
                customStart = LocalTime.of(7, 30),
                customEnd = LocalTime.of(10, 30),
                slotKey = "T0730",
                kind = EntryKind.LESSON,
                subjectLabel = "ALL",
                teacherLabel = "TC-SAT"
            )
        }
        return TimetablePdfService.TimetablePdfModel(
            brand = SchoolBulletinBrandDto(
                schoolId = "school-1",
                nameFr = "Collège Test",
                nameEn = "Test College",
                postalBox = "123",
                phones = listOf("600000000"),
                republicFr = "République du Cameroun",
                republicEn = "Republic of Cameroon",
                mottoFr = "Paix – Travail – Patrie",
                mottoEn = "Peace – Work – Fatherland",
                ministryFr = "MINSEC",
                ministryEn = "MINSEC",
                regionalDelegationFr = "Délégation régionale",
                regionalDelegationEn = "Regional delegation",
                divisionalDelegationFr = null,
                divisionalDelegationEn = null
            ),
            yearLabel = "2026-2027",
            className = if (language == Language.FR) "2NDE C" else "FORM 4 ARTS",
            language = language,
            headerContact = ContactInfo(phones = listOf("699000000", "233000000"), email = "edt@school.test"),
            grid = grid,
            entries = entries
        )
    }

    private fun entry(
        classId: String,
        day: DayOfWeek,
        period: Int,
        subject: String,
        teacher: String,
        optionGroup: String? = null,
        optionCode: String = ""
    ) = TimetableEntry(
        schoolId = "school-1",
        academicYearId = "year-1",
        timetableId = "tt-1",
        classId = classId,
        day = day,
        startPeriod = period,
        periodCount = 1,
        slotKey = "P$period",
        kind = EntryKind.LESSON,
        subjectLabel = subject,
        teacherLabel = teacher,
        optionGroupCode = optionGroup,
        optionCode = optionCode,
        label = LocalizedText(fr = subject, en = subject)
    )

    private fun fixturePeriods(): List<GridPeriod> = listOf(
        GridPeriod(1, PeriodKind.LESSON, LocalTime.of(7, 35), LocalTime.of(8, 25)),
        GridPeriod(2, PeriodKind.LESSON, LocalTime.of(8, 25), LocalTime.of(9, 15)),
        GridPeriod(3, PeriodKind.LESSON, LocalTime.of(9, 15), LocalTime.of(10, 5)),
        GridPeriod(
            null,
            PeriodKind.BREAK,
            LocalTime.of(10, 5),
            LocalTime.of(10, 20),
            LocalizedText("PREMIÈRE PAUSE", "FIRST BREAK")
        ),
        GridPeriod(4, PeriodKind.LESSON, LocalTime.of(10, 20), LocalTime.of(11, 10)),
        GridPeriod(5, PeriodKind.LESSON, LocalTime.of(11, 10), LocalTime.of(12, 0)),
        GridPeriod(6, PeriodKind.LESSON, LocalTime.of(12, 0), LocalTime.of(12, 50)),
        GridPeriod(
            null,
            PeriodKind.BREAK,
            LocalTime.of(12, 50),
            LocalTime.of(13, 20),
            LocalizedText("DEUXIÈME PAUSE", "SECOND BREAK")
        ),
        GridPeriod(7, PeriodKind.LESSON, LocalTime.of(13, 20), LocalTime.of(14, 20)),
        GridPeriod(8, PeriodKind.LESSON, LocalTime.of(14, 20), LocalTime.of(15, 10)),
        GridPeriod(
            null,
            PeriodKind.CLEANUP,
            LocalTime.of(15, 10),
            LocalTime.of(15, 30),
            LocalizedText("NETTOYAGE GÉNÉRAL", "GENERAL CLEAN UP")
        ),
        GridPeriod(
            null,
            PeriodKind.CLOSING,
            LocalTime.of(15, 30),
            LocalTime.of(15, 30),
            LocalizedText("RENTRÉE", "CLOSING")
        )
    )
}
