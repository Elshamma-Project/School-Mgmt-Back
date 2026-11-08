package com.kobe.smels.service.staff

import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }
}
