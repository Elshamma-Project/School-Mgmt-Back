package com.kobe.smels.dto.staff

import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.EmploymentStatus
import com.kobe.smels.model.staff.StaffFunction
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class ContactInfoDto(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)

data class CreateStaffRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:Size(max = 120)
    val firstName: String = "",
    val staffNumber: String? = null,
    val civility: Civility? = null,
    val sex: Sex? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    val functions: Set<StaffFunction> = setOf(StaffFunction.TEACHER),
    val employmentStatus: EmploymentStatus? = null,
    val teachableSubjectIds: Set<String> = emptySet(),
    val hireDate: LocalDate? = null,
    /** SUPER_ADMIN uniquement : cible un établissement. */
    val schoolId: String? = null
)

data class UpdateStaffRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:Size(max = 120)
    val firstName: String = "",
    val staffNumber: String? = null,
    val civility: Civility? = null,
    val sex: Sex? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    val functions: Set<StaffFunction> = setOf(StaffFunction.TEACHER),
    val employmentStatus: EmploymentStatus? = null,
    val teachableSubjectIds: Set<String> = emptySet(),
    val hireDate: LocalDate? = null
)

/**
 * Mise à jour limitée de l’identité (self-service profil ou admin).
 * Ne touche pas aux fonctions RH / matières enseignables.
 */
data class UpdateStaffIdentityRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:Size(max = 120)
    val firstName: String = "",
    val civility: Civility? = null,
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto()
)

data class StaffResponse(
    val id: String,
    val schoolId: String,
    val staffNumber: String?,
    val lastName: String,
    val firstName: String,
    val fullName: String,
    val civility: Civility?,
    val sex: Sex?,
    val dateOfBirth: LocalDate?,
    val contact: ContactInfoDto,
    val functions: Set<StaffFunction>,
    val employmentStatus: EmploymentStatus?,
    val teachableSubjectIds: Set<String>,
    val hireDate: LocalDate?,
    val active: Boolean,
    /** Enrichi depuis le compte lié (`user`), null si aucun compte ou jamais connecté. */
    val lastLoginAt: Instant? = null
)

/** DTO public inter-modules (pas de contact détaillé). */
data class StaffSummaryDto(
    val id: String,
    val schoolId: String,
    val displayName: String,
    val civility: Civility?,
    val functions: Set<StaffFunction>,
    val active: Boolean
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
