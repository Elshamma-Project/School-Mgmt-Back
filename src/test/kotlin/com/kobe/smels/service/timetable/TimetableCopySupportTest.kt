package com.kobe.smels.service.timetable

import com.kobe.smels.model.timetable.EntryKind
import com.kobe.smels.model.timetable.TimetableEntry
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TimetableCopySupportTest {

    @Test
    fun `remaps session groups consistently`() {
        val a = entry("c1", sessionGroupId = "sg-old")
        val b = entry("c2", sessionGroupId = "sg-old")
        val c = entry("c3", sessionGroupId = null)
        val clones = TimetableCopySupport.cloneEntries(
            sourceEntries = listOf(a, b, c),
            targetSchoolId = "school-1",
            targetAcademicYearId = "year-2",
            targetTimetableId = "tt-new"
        )
        assertEquals(3, clones.size)
        assertEquals("tt-new", clones[0].timetableId)
        assertEquals("year-2", clones[0].academicYearId)
        assertNull(clones[0].id)
        assertNotEquals("sg-old", clones[0].sessionGroupId)
        assertEquals(clones[0].sessionGroupId, clones[1].sessionGroupId)
        assertNull(clones[2].sessionGroupId)
    }

    private fun entry(classId: String, sessionGroupId: String?) = TimetableEntry(
        schoolId = "school-1",
        academicYearId = "year-1",
        timetableId = "tt-old",
        classId = classId,
        day = DayOfWeek.MONDAY,
        startPeriod = 1,
        slotKey = "P1",
        kind = EntryKind.LESSON,
        sessionGroupId = sessionGroupId
    )
}
