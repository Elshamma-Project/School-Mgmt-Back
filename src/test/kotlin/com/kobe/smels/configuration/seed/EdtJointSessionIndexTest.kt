package com.kobe.smels.configuration.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class EdtJointSessionIndexTest {

    @Test
    fun `multi-period hints share the same session group`() {
        val extraction = EdtExtractionFile(
            jointSessions = listOf(
                EdtJointSession(
                    id = "J-002",
                    day = "THURSDAY",
                    period = 3,
                    teacherKey = "ENELI",
                    printedAlias = "LCN",
                    classes = listOf("5ème", "6ème")
                ),
                EdtJointSession(
                    id = "J-003",
                    day = "THURSDAY",
                    period = 4,
                    teacherKey = "ENELI",
                    printedAlias = "LCN",
                    classes = listOf("5ème", "6ème")
                )
            ),
            entries = listOf(
                EdtEntry(
                    `class` = "6ème",
                    day = "THURSDAY",
                    startPeriod = 3,
                    periodCount = 2,
                    items = listOf(
                        EdtEntryItem(
                            printedAlias = "LCN",
                            teacher = EdtItemTeacher(key = "ENELI"),
                            jointSessionHint = listOf("J-002", "J-003")
                        )
                    )
                ),
                EdtEntry(
                    `class` = "5ème",
                    day = "THURSDAY",
                    startPeriod = 3,
                    periodCount = 1,
                    items = listOf(
                        EdtEntryItem(
                            printedAlias = "LCN",
                            teacher = EdtItemTeacher(key = "ENELI"),
                            jointSessionHint = "J-002"
                        )
                    )
                )
            )
        )
        val index = EdtJointSessionIndex(extraction)
        val g6 = index.resolveGroupId(
            className = "6ème",
            day = "THURSDAY",
            startPeriod = 3,
            periodCount = 2,
            teacherKey = "ENELI",
            printedAlias = "LCN",
            hints = listOf("J-002", "J-003")
        )
        val g5 = index.resolveGroupId(
            className = "5ème",
            day = "THURSDAY",
            startPeriod = 3,
            periodCount = 1,
            teacherKey = "ENELI",
            printedAlias = "LCN",
            hints = listOf("J-002")
        )
        assertNotNull(g6)
        assertNotNull(g5)
        assertEquals(g6, g5)
    }

    @Test
    fun `unrelated joints stay in different groups`() {
        val extraction = EdtExtractionFile(
            jointSessions = listOf(
                EdtJointSession(
                    id = "J-010",
                    day = "MONDAY",
                    period = 1,
                    teacherKey = "A",
                    printedAlias = "MATHS",
                    classes = listOf("Form 1", "Form 2")
                ),
                EdtJointSession(
                    id = "J-020",
                    day = "FRIDAY",
                    period = 8,
                    teacherKey = "B",
                    printedAlias = "EPS",
                    classes = listOf("3ème", "4ème")
                )
            )
        )
        val index = EdtJointSessionIndex(extraction)
        val a = index.resolveGroupId(
            className = "Form 1",
            day = "MONDAY",
            startPeriod = 1,
            periodCount = 1,
            teacherKey = "A",
            printedAlias = "MATHS",
            hints = listOf("J-010")
        )
        val b = index.resolveGroupId(
            className = "3ème",
            day = "FRIDAY",
            startPeriod = 8,
            periodCount = 1,
            teacherKey = "B",
            printedAlias = "EPS",
            hints = listOf("J-020")
        )
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a, b)
    }
}
