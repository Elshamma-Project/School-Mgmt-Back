package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.ContactInfoDto
import com.kobe.smels.dto.timetable.GridPeriodDto
import com.kobe.smels.dto.timetable.LocalizedTextDto
import com.kobe.smels.dto.timetable.TimeSlotGridResponse
import com.kobe.smels.dto.timetable.TimetableEntryResponse
import com.kobe.smels.dto.timetable.TimetableResponse
import com.kobe.smels.dto.timetable.TimetableSubjectAliasResponse
import com.kobe.smels.dto.timetable.TimetableSummaryDto
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.timetable.GridPeriod
import com.kobe.smels.model.timetable.TimeSlotGrid
import com.kobe.smels.model.timetable.Timetable
import com.kobe.smels.model.timetable.TimetableEntry
import com.kobe.smels.model.timetable.TimetableSubjectAlias

internal object TimetableMapper {
    fun toLocalized(dto: LocalizedTextDto?): LocalizedText? =
        dto?.let { LocalizedText(fr = it.fr?.trim()?.ifBlank { null }, en = it.en?.trim()?.ifBlank { null }) }

    fun toLocalizedDto(text: LocalizedText?): LocalizedTextDto? =
        text?.let { LocalizedTextDto(fr = it.fr, en = it.en) }

    fun toContact(dto: ContactInfoDto?): ContactInfo? =
        dto?.let {
            ContactInfo(
                phones = it.phones.map { p -> p.trim() }.filter { p -> p.isNotBlank() },
                email = it.email?.trim()?.ifBlank { null },
                address = it.address?.trim()?.ifBlank { null },
                city = it.city?.trim()?.ifBlank { null }
            )
        }

    fun toContactDto(contact: ContactInfo?): ContactInfoDto? =
        contact?.let { ContactInfoDto(it.phones, it.email, it.address, it.city) }

    fun toPeriod(dto: GridPeriodDto): GridPeriod =
        GridPeriod(
            number = dto.number,
            kind = dto.kind,
            start = dto.start,
            end = dto.end,
            label = toLocalized(dto.label)
        )

    fun toPeriodDto(period: GridPeriod): GridPeriodDto =
        GridPeriodDto(
            number = period.number,
            kind = period.kind,
            start = period.start,
            end = period.end,
            label = toLocalizedDto(period.label)
        )

    fun toGridResponse(grid: TimeSlotGrid): TimeSlotGridResponse =
        TimeSlotGridResponse(
            id = grid.id!!,
            schoolId = grid.schoolId,
            academicYearId = grid.academicYearId,
            name = grid.name,
            days = grid.days,
            periods = grid.periods.map { toPeriodDto(it) },
            active = grid.active
        )

    fun toTimetableResponse(timetable: Timetable): TimetableResponse =
        TimetableResponse(
            id = timetable.id!!,
            schoolId = timetable.schoolId,
            academicYearId = timetable.academicYearId,
            gridId = timetable.gridId,
            revision = timetable.revision,
            status = timetable.status,
            generatedAt = timetable.generatedAt,
            generatedBy = timetable.generatedBy,
            publishedAt = timetable.publishedAt,
            publishedBy = timetable.publishedBy,
            headerContact = toContactDto(timetable.headerContact),
            note = timetable.note
        )

    fun toSummary(timetable: Timetable): TimetableSummaryDto =
        TimetableSummaryDto(
            id = timetable.id!!,
            academicYearId = timetable.academicYearId,
            revision = timetable.revision,
            status = timetable.status
        )

    fun toEntryResponse(entry: TimetableEntry): TimetableEntryResponse =
        TimetableEntryResponse(
            id = entry.id!!,
            schoolId = entry.schoolId,
            academicYearId = entry.academicYearId,
            timetableId = entry.timetableId,
            classId = entry.classId,
            day = entry.day,
            startPeriod = entry.startPeriod,
            periodCount = entry.periodCount,
            customStart = entry.customStart,
            customEnd = entry.customEnd,
            slotKey = entry.slotKey,
            kind = entry.kind,
            classSubjectIds = entry.classSubjectIds,
            subjectId = entry.subjectId,
            teacherId = entry.teacherId,
            roomId = entry.roomId,
            activityType = entry.activityType,
            sessionGroupId = entry.sessionGroupId,
            optionGroupCode = entry.optionGroupCode,
            optionCode = entry.optionCode,
            label = toLocalizedDto(entry.label),
            note = entry.note,
            subjectLabel = entry.subjectLabel,
            teacherLabel = entry.teacherLabel
        )

    fun toAliasResponse(alias: TimetableSubjectAlias): TimetableSubjectAliasResponse =
        TimetableSubjectAliasResponse(
            id = alias.id!!,
            schoolId = alias.schoolId,
            academicYearId = alias.academicYearId,
            section = alias.section,
            sectionKey = alias.sectionKey,
            aliasNormalized = alias.aliasNormalized,
            aliasVariants = alias.aliasVariants,
            classSubjectIds = alias.classSubjectIds,
            unresolved = alias.unresolved,
            note = alias.note,
            active = alias.active
        )
}
