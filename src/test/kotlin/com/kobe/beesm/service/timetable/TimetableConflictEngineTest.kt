package com.kobe.beesm.service.timetable

import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.model.timetable.EntryKind
import com.kobe.beesm.model.timetable.GridPeriod
import com.kobe.beesm.model.timetable.PeriodKind
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** BR-09 / BR-65 / BR-66 — conflits purs. */
class TimetableConflictEngineTest {

    private val periods = listOf(
        GridPeriod(1, PeriodKind.LESSON, LocalTime.of(7, 35), LocalTime.of(8, 25)),
        GridPeriod(2, PeriodKind.LESSON, LocalTime.of(8, 25), LocalTime.of(9, 15)),
        GridPeriod(3, PeriodKind.LESSON, LocalTime.of(9, 15), LocalTime.of(10, 5)),
        GridPeriod(null, PeriodKind.BREAK, LocalTime.of(10, 5), LocalTime.of(10, 20)),
        GridPeriod(4, PeriodKind.LESSON, LocalTime.of(10, 20), LocalTime.of(11, 10))
    )

    @Test
    fun `class overlap is blocking`() {
        val a = candidate("e1", classId = "c1", startPeriod = 1)
        val b = candidate("e2", classId = "c1", startPeriod = 1)
        val issues = TimetableConflictEngine.findBlocking(a, listOf(b))
        assertTrue(issues.any { it.code == "CLASS_OVERLAP" })
    }

    @Test
    fun `parallel LV2 options do not conflict for class`() {
        val all = candidate(
            "e1",
            classId = "c1",
            startPeriod = 2,
            optionGroupCode = "LV2",
            optionCode = "ALL"
        )
        val esp = candidate(
            "e2",
            classId = "c1",
            startPeriod = 2,
            optionGroupCode = "LV2",
            optionCode = "ESP"
        )
        val issues = TimetableConflictEngine.findBlocking(all, listOf(esp))
        assertTrue(issues.none { it.code == "CLASS_OVERLAP" })
    }

    @Test
    fun `teacher overlap is blocking`() {
        val a = candidate("e1", classId = "c1", startPeriod = 1, teacherId = "t1")
        val b = candidate("e2", classId = "c2", startPeriod = 1, teacherId = "t1")
        val issues = TimetableConflictEngine.findBlocking(a, listOf(b))
        assertTrue(issues.any { it.code == "TEACHER_OVERLAP" })
    }

    @Test
    fun `same sessionGroupId skips teacher and room conflict`() {
        val a = candidate(
            "e1",
            classId = "c1",
            startPeriod = 1,
            teacherId = "t1",
            roomId = "r1",
            sessionGroupId = "sg1"
        )
        val b = candidate(
            "e2",
            classId = "c2",
            startPeriod = 1,
            teacherId = "t1",
            roomId = "r1",
            sessionGroupId = "sg1"
        )
        val issues = TimetableConflictEngine.findBlocking(a, listOf(b))
        assertTrue(issues.none { it.code == "TEACHER_OVERLAP" || it.code == "ROOM_OVERLAP" })
    }

    @Test
    fun `ACTIVITY does not engage teacher for conflict`() {
        val lesson = candidate("e1", classId = "c1", startPeriod = 1, teacherId = "t1")
        val activity = candidate(
            "e2",
            classId = "c2",
            startPeriod = 1,
            teacherId = "t1",
            kind = EntryKind.ACTIVITY
        )
        val issues = TimetableConflictEngine.findBlocking(lesson, listOf(activity))
        assertTrue(issues.none { it.code == "TEACHER_OVERLAP" })
    }

    @Test
    fun `room overlap is blocking`() {
        val a = candidate("e1", classId = "c1", startPeriod = 1, roomId = "r1")
        val b = candidate("e2", classId = "c2", startPeriod = 1, roomId = "r1")
        val issues = TimetableConflictEngine.findBlocking(a, listOf(b))
        assertTrue(issues.any { it.code == "ROOM_OVERLAP" })
    }

    @Test
    fun `double period overlaps next period`() {
        val double = candidate("e1", classId = "c1", startPeriod = 1, periodCount = 2, teacherId = "t1")
        val next = candidate("e2", classId = "c2", startPeriod = 2, teacherId = "t1")
        val issues = TimetableConflictEngine.findBlocking(double, listOf(next))
        assertTrue(issues.any { it.code == "TEACHER_OVERLAP" })
    }

    @Test
    fun `custom saturday intervals conflict on real time`() {
        val a = TimetableConflictEngine.Candidate(
            id = "e1",
            classId = "c1",
            day = DayOfWeek.SATURDAY,
            start = LocalTime.of(7, 30),
            end = LocalTime.of(10, 30),
            kind = EntryKind.LESSON,
            teacherId = "t1",
            roomId = null,
            sessionGroupId = null,
            optionGroupCode = null,
            optionCode = ""
        )
        val b = a.copy(
            id = "e2",
            classId = "c2",
            start = LocalTime.of(9, 0),
            end = LocalTime.of(11, 0)
        )
        val issues = TimetableConflictEngine.findBlocking(a, listOf(b))
        assertTrue(issues.any { it.code == "TEACHER_OVERLAP" })
    }

    @Test
    fun `BR-66 rejects entry entirely inside break`() {
        assertFailsWith<BusinessRuleException> {
            TimetableConflictEngine.assertNotOnBreakOrCleanup(
                startPeriod = null,
                customStart = LocalTime.of(10, 5),
                customEnd = LocalTime.of(10, 20),
                day = DayOfWeek.MONDAY,
                gridDays = setOf(DayOfWeek.MONDAY),
                periods = periods
            )
        }
    }

    @Test
    fun `resolveInterval uses first and last lesson wall clock`() {
        val (start, end) = TimetableConflictEngine.resolveInterval(1, 2, null, null, periods)
        assertEquals(LocalTime.of(7, 35), start)
        assertEquals(LocalTime.of(9, 15), end)
    }

    private fun candidate(
        id: String,
        classId: String,
        startPeriod: Int,
        periodCount: Int = 1,
        teacherId: String? = null,
        roomId: String? = null,
        sessionGroupId: String? = null,
        optionGroupCode: String? = null,
        optionCode: String = "",
        kind: EntryKind = EntryKind.LESSON,
        day: DayOfWeek = DayOfWeek.MONDAY
    ): TimetableConflictEngine.Candidate {
        val (start, end) = TimetableConflictEngine.resolveInterval(
            startPeriod,
            periodCount,
            null,
            null,
            periods
        )
        return TimetableConflictEngine.Candidate(
            id = id,
            classId = classId,
            day = day,
            start = start,
            end = end,
            kind = kind,
            teacherId = teacherId,
            roomId = roomId,
            sessionGroupId = sessionGroupId,
            optionGroupCode = optionGroupCode,
            optionCode = optionCode,
            periodCount = periodCount
        )
    }
}
