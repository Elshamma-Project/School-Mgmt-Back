package com.kobe.beesm.service.timetable

import com.kobe.beesm.exception.BusinessRuleException
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SlotKeysTest {

    @Test
    fun `period slot key`() {
        assertEquals("P4", SlotKeys.fromPeriod(4))
        assertEquals("P1", SlotKeys.resolve(1, null))
    }

    @Test
    fun `custom time slot key`() {
        assertEquals("T0730", SlotKeys.fromCustomStart(LocalTime.of(7, 30)))
        assertEquals("T1030", SlotKeys.resolve(null, LocalTime.of(10, 30)))
    }

    @Test
    fun `ambiguous slot rejected`() {
        assertFailsWith<BusinessRuleException> {
            SlotKeys.resolve(2, LocalTime.of(8, 0))
        }
    }

    @Test
    fun `missing slot rejected`() {
        assertFailsWith<BusinessRuleException> {
            SlotKeys.resolve(null, null)
        }
    }
}
