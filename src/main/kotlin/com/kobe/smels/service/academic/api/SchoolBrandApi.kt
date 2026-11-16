package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.SchoolBulletinBrandDto
import com.kobe.smels.dto.academic.SchoolTimetableSettingsDto
import reactor.core.publisher.Mono

/** Branding école + réglages croisés — frontière reporting/timetable ↔ academic. */
interface SchoolBrandApi {
    fun getBulletinBrand(schoolId: String): Mono<SchoolBulletinBrandDto>

    /** Q-32 : le module timetable lit ce flag sans toucher `model.academic`. */
    fun getTimetableSettings(schoolId: String): Mono<SchoolTimetableSettingsDto>
}
