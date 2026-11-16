package com.kobe.beesm.service.timetable

import kotlin.test.Test
import kotlin.test.assertEquals

class TimetableSubjectAliasNormalizeTest {

    @Test
    fun `strips accents and uppercases`() {
        assertEquals(
            "FRANCAIS",
            TimetableSubjectAliasService.normalizeAlias(" Français ")
        )
    }
}
