package com.kobe.smels.service.staff

import com.kobe.smels.dto.staff.ContactInfoDto
import com.kobe.smels.dto.staff.StaffResponse
import com.kobe.smels.dto.staff.StaffSummaryDto
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.staff.Staff
import java.time.Instant

object StaffMapper {
    fun toContact(dto: ContactInfoDto): ContactInfo =
        ContactInfo(
            phones = dto.phones.map { it.trim() }.filter { it.isNotBlank() },
            email = dto.email?.trim()?.ifBlank { null },
            address = dto.address?.trim()?.ifBlank { null },
            city = dto.city?.trim()?.ifBlank { null }
        )

    fun toContactDto(contact: ContactInfo): ContactInfoDto =
        ContactInfoDto(
            phones = contact.phones,
            email = contact.email,
            address = contact.address,
            city = contact.city
        )

    fun toResponse(staff: Staff, lastLoginAt: Instant? = null): StaffResponse =
        StaffResponse(
            id = staff.id!!,
            schoolId = staff.schoolId,
            staffNumber = staff.staffNumber,
            lastName = staff.lastName,
            firstName = staff.firstName,
            fullName = staff.fullName(),
            civility = staff.civility,
            sex = staff.sex,
            dateOfBirth = staff.dateOfBirth,
            contact = toContactDto(staff.contact),
            functions = staff.functions,
            employmentStatus = staff.employmentStatus,
            teachableSubjectIds = staff.teachableSubjectIds,
            hireDate = staff.hireDate,
            active = staff.active,
            lastLoginAt = lastLoginAt
        )

    fun toSummary(staff: Staff): StaffSummaryDto =
        StaffSummaryDto(
            id = staff.id!!,
            schoolId = staff.schoolId,
            displayName = staff.fullName(),
            civility = staff.civility,
            functions = staff.functions,
            active = staff.active
        )
}
