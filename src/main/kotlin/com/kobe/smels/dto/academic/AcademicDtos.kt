package com.kobe.smels.dto.academic

import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.academic.RoomType
import com.kobe.smels.model.academic.SubjectKind
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class LocalizedTextDto(
    val fr: String? = null,
    val en: String? = null
)

data class ContactInfoDto(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)

data class OfficialHeaderDto(
    @field:NotEmpty
    val cycles: Set<Cycle>,
    @field:Valid
    val republic: LocalizedTextDto,
    @field:Valid
    val motto: LocalizedTextDto,
    @field:Valid
    val ministry: LocalizedTextDto,
    @field:Valid
    val regionalDelegation: LocalizedTextDto? = null,
    @field:Valid
    val divisionalDelegation: LocalizedTextDto? = null
)

data class CreateSchoolRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val code: String,
    @field:Valid
    val name: LocalizedTextDto,
    val shortName: String? = null,
    @field:Valid
    val officialHeaders: List<OfficialHeaderDto> = emptyList(),
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    val postalBox: String? = null,
    val signaturePlace: String? = null,
    @field:Valid
    val headTitle: LocalizedTextDto? = null,
    val headOfSchoolStaffId: String? = null,
    val logoFileId: String? = null,
    @field:Valid
    val reportCardNotice: LocalizedTextDto? = null,
    @field:Valid
    val reportCardDisclaimer: LocalizedTextDto? = null,
    val timezone: String = "Africa/Douala"
)

data class UpdateSchoolRequest(
    @field:Valid
    val name: LocalizedTextDto,
    val shortName: String? = null,
    @field:Valid
    val officialHeaders: List<OfficialHeaderDto> = emptyList(),
    @field:Valid
    val contact: ContactInfoDto = ContactInfoDto(),
    val postalBox: String? = null,
    val signaturePlace: String? = null,
    @field:Valid
    val headTitle: LocalizedTextDto? = null,
    val headOfSchoolStaffId: String? = null,
    val logoFileId: String? = null,
    @field:Valid
    val reportCardNotice: LocalizedTextDto? = null,
    @field:Valid
    val reportCardDisclaimer: LocalizedTextDto? = null,
    val timezone: String = "Africa/Douala",
    val active: Boolean = true
)

data class SchoolResponse(
    val id: String,
    val code: String,
    val name: LocalizedTextDto,
    val shortName: String?,
    val officialHeaders: List<OfficialHeaderDto>,
    val contact: ContactInfoDto,
    val postalBox: String?,
    val signaturePlace: String?,
    val headTitle: LocalizedTextDto?,
    val headOfSchoolStaffId: String?,
    val logoFileId: String?,
    val reportCardNotice: LocalizedTextDto?,
    val reportCardDisclaimer: LocalizedTextDto?,
    val timezone: String,
    val active: Boolean
)

data class TermDto(
    @field:Min(1)
    @field:Max(3)
    val number: Int,
    @field:Valid
    val name: LocalizedTextDto,
    @field:NotNull
    val startDate: LocalDate,
    @field:NotNull
    val endDate: LocalDate
)

data class CreateAcademicYearRequest(
    @field:NotBlank
    val label: String,
    @field:NotNull
    val startDate: LocalDate,
    @field:NotNull
    val endDate: LocalDate,
    val current: Boolean = false,
    val status: AcademicYearStatus = AcademicYearStatus.PLANNED,
    @field:Valid
    @field:NotEmpty
    val terms: List<TermDto>,
    val schoolId: String? = null
)

data class UpdateAcademicYearRequest(
    @field:NotBlank
    val label: String,
    @field:NotNull
    val startDate: LocalDate,
    @field:NotNull
    val endDate: LocalDate,
    val current: Boolean = false,
    val status: AcademicYearStatus = AcademicYearStatus.PLANNED,
    @field:Valid
    @field:NotEmpty
    val terms: List<TermDto>
)

data class AcademicYearResponse(
    val id: String,
    val schoolId: String,
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val current: Boolean,
    val status: AcademicYearStatus,
    val terms: List<TermDto>
)

data class AcademicYearSummaryDto(
    val id: String,
    val schoolId: String,
    val label: String,
    val current: Boolean,
    val status: AcademicYearStatus
)

data class CreateLevelRequest(
    @field:NotBlank
    val code: String,
    @field:Valid
    val name: LocalizedTextDto,
    @field:NotNull
    val cycle: Cycle,
    @field:NotNull
    val section: Section,
    @field:Min(1)
    val order: Int,
    val equivalenceIndex: Int? = null,
    val nextLevelCode: String? = null,
    val defaultGradingSchemeId: String? = null,
    val schoolId: String? = null
)

data class UpdateLevelRequest(
    @field:Valid
    val name: LocalizedTextDto,
    @field:NotNull
    val cycle: Cycle,
    @field:NotNull
    val section: Section,
    @field:Min(1)
    val order: Int,
    val equivalenceIndex: Int? = null,
    val nextLevelCode: String? = null,
    val defaultGradingSchemeId: String? = null
)

data class LevelResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val cycle: Cycle,
    val section: Section,
    val order: Int,
    val equivalenceIndex: Int?,
    val nextLevelCode: String?,
    val defaultGradingSchemeId: String?,
    val active: Boolean
)

data class LevelSummaryDto(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val cycle: Cycle,
    val section: Section,
    val active: Boolean
)

data class CreateSchoolClassRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val levelId: String,
    @field:NotBlank
    val name: String,
    val trackCode: String? = null,
    val capacity: Int? = null,
    val mainTeacherId: String? = null,
    val homeRoomId: String? = null,
    @field:NotNull
    val defaultLanguage: Language,
    val availableLanguages: Set<Language> = setOf(Language.FR, Language.EN),
    val gradingSchemeId: String? = null,
    val schoolId: String? = null
)

data class UpdateSchoolClassRequest(
    @field:NotBlank
    val name: String,
    val trackCode: String? = null,
    val capacity: Int? = null,
    val mainTeacherId: String? = null,
    val homeRoomId: String? = null,
    @field:NotNull
    val defaultLanguage: Language,
    val availableLanguages: Set<Language> = setOf(Language.FR, Language.EN),
    val gradingSchemeId: String? = null
)

data class SchoolClassResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val levelId: String,
    val levelCode: String,
    val section: Section,
    val cycle: Cycle,
    val name: String,
    val trackCode: String?,
    val capacity: Int?,
    val mainTeacherId: String?,
    val homeRoomId: String?,
    val defaultLanguage: Language,
    val availableLanguages: Set<Language>,
    val gradingSchemeId: String?,
    val active: Boolean
)

data class SchoolClassSummaryDto(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val levelId: String,
    val levelCode: String,
    val name: String,
    val section: Section,
    val defaultLanguage: Language,
    val gradingSchemeId: String? = null,
    val active: Boolean
)

data class CreateRoomRequest(
    @field:NotBlank
    val name: String,
    val type: RoomType = RoomType.CLASSROOM,
    val capacity: Int? = null,
    val schoolId: String? = null
)

data class UpdateRoomRequest(
    @field:NotBlank
    val name: String,
    val type: RoomType = RoomType.CLASSROOM,
    val capacity: Int? = null
)

data class RoomResponse(
    val id: String,
    val schoolId: String,
    val name: String,
    val type: RoomType,
    val capacity: Int?,
    val active: Boolean
)

data class CreateSubjectRequest(
    @field:NotBlank
    val code: String,
    @field:Valid
    val name: LocalizedTextDto,
    @field:Valid
    val shortName: LocalizedTextDto? = null,
    val timetableAlias: String? = null,
    val kind: SubjectKind = SubjectKind.ACADEMIC,
    val teachingLanguage: Language? = null,
    val schoolId: String? = null
)

data class UpdateSubjectRequest(
    @field:Valid
    val name: LocalizedTextDto,
    @field:Valid
    val shortName: LocalizedTextDto? = null,
    val timetableAlias: String? = null,
    val kind: SubjectKind = SubjectKind.ACADEMIC,
    val teachingLanguage: Language? = null
)

data class SubjectResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val shortName: LocalizedTextDto?,
    val timetableAlias: String?,
    val kind: SubjectKind,
    val teachingLanguage: Language?,
    val active: Boolean
)

data class SubjectSummaryDto(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val kind: SubjectKind,
    val active: Boolean
)

data class CreateSubjectGroupRequest(
    @field:NotBlank
    val code: String,
    @field:Valid
    val name: LocalizedTextDto,
    val displayOrder: Int = 0,
    val schoolId: String? = null
)

data class UpdateSubjectGroupRequest(
    @field:Valid
    val name: LocalizedTextDto,
    val displayOrder: Int = 0
)

data class SubjectGroupResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val displayOrder: Int,
    val active: Boolean
)

data class CreateClassSubjectRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val classId: String,
    @field:NotBlank
    val subjectId: String,
    @field:NotBlank
    val groupId: String,
    @field:NotNull
    @field:DecimalMin(value = "0.0", inclusive = false)
    val coefficient: BigDecimal,
    val teacherId: String? = null,
    val weeklyHours: Int? = null,
    val teachingLanguage: Language? = null,
    val displayOrder: Int = 0,
    val maxScore: BigDecimal = BigDecimal("20"),
    val countsInAverage: Boolean = true,
    val optional: Boolean = false,
    val schoolId: String? = null
)

data class UpdateClassSubjectRequest(
    @field:NotBlank
    val groupId: String,
    @field:NotNull
    @field:DecimalMin(value = "0.0", inclusive = false)
    val coefficient: BigDecimal,
    val teacherId: String? = null,
    val weeklyHours: Int? = null,
    val teachingLanguage: Language? = null,
    val displayOrder: Int = 0,
    val maxScore: BigDecimal = BigDecimal("20"),
    val countsInAverage: Boolean = true,
    val optional: Boolean = false
)

data class ClassSubjectResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val classId: String,
    val subjectId: String,
    val groupId: String,
    val coefficient: BigDecimal,
    val teacherId: String?,
    val weeklyHours: Int?,
    val teachingLanguage: Language?,
    val displayOrder: Int,
    val maxScore: BigDecimal,
    val countsInAverage: Boolean,
    val optional: Boolean,
    val active: Boolean
)

data class ClassSubjectSummaryDto(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val classId: String,
    val subjectId: String,
    val groupId: String,
    val coefficient: BigDecimal,
    val teacherId: String?,
    val weeklyHours: Int?,
    val maxScore: BigDecimal,
    val countsInAverage: Boolean,
    val active: Boolean
)

data class UpsertTermCompetencyRequest(
    @field:NotBlank
    val classSubjectId: String,
    @field:Min(1)
    @field:Max(3)
    val termNumber: Int,
    @field:Valid
    val text: LocalizedTextDto,
    val schoolId: String? = null
)

data class TermCompetencyResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val classId: String,
    val classSubjectId: String,
    val termNumber: Int,
    val text: LocalizedTextDto,
    val authorStaffId: String?
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
