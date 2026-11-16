package com.kobe.smels.service.staff

import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.staff.StaffSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffMapperTest {
    @Test
    fun `full name and summary mapping`() {
        val staff = Staff(
            id = "s1",
            schoolId = "school-1",
            lastName = "NGWA",
            firstName = "Hariette",
            civility = Civility.MME,
            functions = setOf(StaffFunction.TEACHER),
            active = true
        )
        val response = StaffMapper.toResponse(staff)
        val summary = StaffMapper.toSummary(staff)

        assertEquals("NGWA Hariette", response.fullName)
        assertEquals("NGWA Hariette", summary.displayName)
        assertEquals(Civility.MME, summary.civility)
        assertTrue(summary.active)
        assertFalse(response.needsCompletion)
        assertNull(response.source)
        assertNull(response.seedKey)
        assertFalse(response.hasLoginAccount)
    }

    @Test
    fun `maps seed fields for incomplete EDT teacher without inventing identity`() {
        val staff = Staff(
            id = "s2",
            schoolId = "school-1",
            lastName = "DEMO-TEACHER",
            firstName = "",
            civility = Civility.MR,
            functions = setOf(StaffFunction.TEACHER),
            seedKey = "demo-key",
            source = StaffSource.EDT_2026_2027,
            needsCompletion = true,
            active = true
        )
        val response = StaffMapper.toResponse(staff)
        assertEquals("demo-key", response.seedKey)
        assertEquals(StaffSource.EDT_2026_2027, response.source)
        assertTrue(response.needsCompletion)
        assertEquals("", response.firstName)
        assertFalse(response.hasLoginAccount)
    }
}
