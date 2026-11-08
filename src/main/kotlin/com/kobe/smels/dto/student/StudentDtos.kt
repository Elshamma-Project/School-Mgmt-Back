package com.kobe.smels.dto.student

import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.GuardianRelationship
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class ContactInfoDto(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)

data class GuardianLinkDto(
    @field:NotBlank
    val guardianId: String,
    val relationship: GuardianRelationship = GuardianRelationship.OTHER,
    val primary: Boolean = false,
    val canViewGrades: Boolean = true,
    val canDownloadReportCards: Boolean = true
)

data class CreateStudentRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val matricule: String,
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:NotBlank
    @field:Size(max = 120)
    val firstName: String,
    @field:NotNull
    val sex: Sex,
    val dateOfBirth: LocalDate? = null,
    val placeOfBirth: String? = null,
    val nationality: String? = null,
    val photoFileId: String? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    @field:Valid
    val guardians: List<GuardianLinkDto> = emptyList(),
    /** SUPER_ADMIN uniquement. */
    val schoolId: String? = null
)

data class UpdateStudentRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val matricule: String,
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:NotBlank
    @field:Size(max = 120)
    val firstName: String,
    @field:NotNull
    val sex: Sex,
    val dateOfBirth: LocalDate? = null,
    val placeOfBirth: String? = null,
    val nationality: String? = null,
    val photoFileId: String? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    @field:Valid
    val guardians: List<GuardianLinkDto> = emptyList()
)

data class StudentResponse(
    val id: String,
    val schoolId: String,
    val matricule: String,
    val lastName: String,
    val firstName: String,
    val fullName: String,
    val sex: Sex,
    val dateOfBirth: LocalDate?,
    val placeOfBirth: String?,
    val nationality: String?,
    val photoFileId: String?,
    val contact: ContactInfoDto,
    val guardians: List<GuardianLinkDto>,
    val active: Boolean
)

/** DTO public inter-modules (docs/08) — pas de coordonnées détaillées. */
data class StudentSummaryDto(
    val id: String,
    val schoolId: String,
    val matricule: String,
    val displayName: String,
    val sex: Sex,
    val active: Boolean
)

data class CreateGuardianRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:Size(max = 120)
    val firstName: String = "",
    val sex: Sex? = null,
    val profession: String? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    val schoolId: String? = null
)

data class UpdateGuardianRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:Size(max = 120)
    val firstName: String = "",
    val sex: Sex? = null,
    val profession: String? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto()
)

data class GuardianResponse(
    val id: String,
    val schoolId: String,
    val lastName: String,
    val firstName: String,
    val fullName: String,
    val sex: Sex?,
    val profession: String?,
    val contact: ContactInfoDto,
    val active: Boolean
)

data class GuardianSummaryDto(
    val id: String,
    val schoolId: String,
    val displayName: String,
    val active: Boolean
)

data class CreateEnrollmentRequest(
    @field:NotBlank
    val studentId: String,
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val classId: String,
    @field:NotBlank
    val levelId: String,
    val rollNumber: Int? = null,
    val repeater: Boolean = false,
    val newStudent: Boolean = false,
    val enrolledOn: LocalDate? = null,
    val previousSchool: String? = null,
    val schoolId: String? = null
)

data class UpdateEnrollmentRequest(
    @field:NotBlank
    val classId: String,
    @field:NotBlank
    val levelId: String,
    val rollNumber: Int? = null,
    val repeater: Boolean = false,
    val newStudent: Boolean = false,
    val status: EnrollmentStatus = EnrollmentStatus.ENROLLED,
    val enrolledOn: LocalDate? = null,
    val leftOn: LocalDate? = null,
    val previousSchool: String? = null
)

data class EnrollmentResponse(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val academicYearId: String,
    val classId: String,
    val levelId: String,
    val rollNumber: Int?,
    val repeater: Boolean,
    val newStudent: Boolean,
    val status: EnrollmentStatus,
    val enrolledOn: LocalDate?,
    val leftOn: LocalDate?,
    val previousSchool: String?
)

data class EnrollmentSummaryDto(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val academicYearId: String,
    val classId: String,
    val levelId: String,
    val status: EnrollmentStatus,
    val repeater: Boolean
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
