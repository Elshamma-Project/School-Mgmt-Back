package com.kobe.smels.service.student

import com.kobe.smels.dto.student.ContactInfoDto
import com.kobe.smels.dto.student.EnrollmentResponse
import com.kobe.smels.dto.student.EnrollmentSummaryDto
import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.dto.student.GuardianResponse
import com.kobe.smels.dto.student.GuardianSummaryDto
import com.kobe.smels.dto.student.StudentResponse
import com.kobe.smels.dto.student.StudentSummaryDto
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.Guardian
import com.kobe.smels.model.student.GuardianLink
import com.kobe.smels.model.student.Student

object StudentMapper {
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

    fun toGuardianLink(dto: GuardianLinkDto): GuardianLink =
        GuardianLink(
            guardianId = dto.guardianId.trim(),
            relationship = dto.relationship,
            primary = dto.primary,
            canViewGrades = dto.canViewGrades,
            canDownloadReportCards = dto.canDownloadReportCards
        )

    fun toGuardianLinkDto(link: GuardianLink): GuardianLinkDto =
        GuardianLinkDto(
            guardianId = link.guardianId,
            relationship = link.relationship,
            primary = link.primary,
            canViewGrades = link.canViewGrades,
            canDownloadReportCards = link.canDownloadReportCards
        )

    fun toStudentResponse(student: Student): StudentResponse =
        StudentResponse(
            id = student.id!!,
            schoolId = student.schoolId,
            matricule = student.matricule,
            lastName = student.lastName,
            firstName = student.firstName,
            fullName = student.fullName(),
            sex = student.sex,
            dateOfBirth = student.dateOfBirth,
            placeOfBirth = student.placeOfBirth,
            nationality = student.nationality,
            photoFileId = student.photoFileId,
            contact = toContactDto(student.contact),
            guardians = student.guardians.map { toGuardianLinkDto(it) },
            active = student.active
        )

    fun toStudentSummary(student: Student): StudentSummaryDto =
        StudentSummaryDto(
            id = student.id!!,
            schoolId = student.schoolId,
            matricule = student.matricule,
            displayName = student.fullName(),
            sex = student.sex,
            active = student.active
        )

    fun toGuardianResponse(guardian: Guardian): GuardianResponse =
        GuardianResponse(
            id = guardian.id!!,
            schoolId = guardian.schoolId,
            lastName = guardian.lastName,
            firstName = guardian.firstName,
            fullName = listOf(guardian.lastName, guardian.firstName).filter { it.isNotBlank() }.joinToString(" "),
            sex = guardian.sex,
            profession = guardian.profession,
            contact = toContactDto(guardian.contact),
            active = guardian.active
        )

    fun toGuardianSummary(guardian: Guardian): GuardianSummaryDto =
        GuardianSummaryDto(
            id = guardian.id!!,
            schoolId = guardian.schoolId,
            displayName = listOf(guardian.lastName, guardian.firstName).filter { it.isNotBlank() }.joinToString(" "),
            active = guardian.active
        )

    fun toEnrollmentResponse(enrollment: Enrollment): EnrollmentResponse =
        EnrollmentResponse(
            id = enrollment.id!!,
            schoolId = enrollment.schoolId,
            studentId = enrollment.studentId,
            academicYearId = enrollment.academicYearId,
            classId = enrollment.classId,
            levelId = enrollment.levelId,
            rollNumber = enrollment.rollNumber,
            repeater = enrollment.repeater,
            newStudent = enrollment.newStudent,
            status = enrollment.status,
            enrolledOn = enrollment.enrolledOn,
            leftOn = enrollment.leftOn,
            previousSchool = enrollment.previousSchool
        )

    fun toEnrollmentSummary(enrollment: Enrollment): EnrollmentSummaryDto =
        EnrollmentSummaryDto(
            id = enrollment.id!!,
            schoolId = enrollment.schoolId,
            studentId = enrollment.studentId,
            academicYearId = enrollment.academicYearId,
            classId = enrollment.classId,
            levelId = enrollment.levelId,
            rollNumber = enrollment.rollNumber,
            status = enrollment.status,
            repeater = enrollment.repeater
        )
}
