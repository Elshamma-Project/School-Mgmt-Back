package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.AcademicYearResponse
import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import com.kobe.smels.dto.academic.ClassSubjectResponse
import com.kobe.smels.dto.academic.ClassSubjectSummaryDto
import com.kobe.smels.dto.academic.ContactInfoDto
import com.kobe.smels.dto.academic.LevelResponse
import com.kobe.smels.dto.academic.LevelSummaryDto
import com.kobe.smels.dto.academic.LocalizedTextDto
import com.kobe.smels.dto.academic.OfficialHeaderDto
import com.kobe.smels.dto.academic.RoomResponse
import com.kobe.smels.dto.academic.SchoolClassResponse
import com.kobe.smels.dto.academic.SchoolClassSummaryDto
import com.kobe.smels.dto.academic.SchoolResponse
import com.kobe.smels.dto.academic.SubjectGroupResponse
import com.kobe.smels.dto.academic.SubjectGroupSummaryDto
import com.kobe.smels.dto.academic.SubjectResponse
import com.kobe.smels.dto.academic.SubjectSummaryDto
import com.kobe.smels.dto.academic.TermCompetencyResponse
import com.kobe.smels.dto.academic.TermDto
import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.Level
import com.kobe.smels.model.academic.OfficialHeader
import com.kobe.smels.model.academic.Room
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.academic.Subject
import com.kobe.smels.model.academic.SubjectGroup
import com.kobe.smels.model.academic.Term
import com.kobe.smels.model.academic.TermCompetency
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.LocalizedText
import java.time.LocalDate

object AcademicMapper {
    fun toLocalized(dto: LocalizedTextDto?): LocalizedText? =
        dto?.let { LocalizedText(fr = it.fr?.trim()?.ifBlank { null }, en = it.en?.trim()?.ifBlank { null }) }

    fun toLocalizedRequired(dto: LocalizedTextDto): LocalizedText =
        LocalizedText(fr = dto.fr?.trim()?.ifBlank { null }, en = dto.en?.trim()?.ifBlank { null })

    fun toLocalizedDto(text: LocalizedText?): LocalizedTextDto? =
        text?.let { LocalizedTextDto(fr = it.fr, en = it.en) }

    fun toContact(dto: ContactInfoDto): ContactInfo =
        ContactInfo(
            phones = dto.phones.map { it.trim() }.filter { it.isNotBlank() },
            email = dto.email?.trim()?.ifBlank { null },
            address = dto.address?.trim()?.ifBlank { null },
            city = dto.city?.trim()?.ifBlank { null }
        )

    fun toContactDto(contact: ContactInfo): ContactInfoDto =
        ContactInfoDto(contact.phones, contact.email, contact.address, contact.city)

    fun toOfficialHeader(dto: OfficialHeaderDto): OfficialHeader =
        OfficialHeader(
            cycles = dto.cycles,
            republic = toLocalizedRequired(dto.republic),
            motto = toLocalizedRequired(dto.motto),
            ministry = toLocalizedRequired(dto.ministry),
            regionalDelegation = toLocalized(dto.regionalDelegation),
            divisionalDelegation = toLocalized(dto.divisionalDelegation)
        )

    fun toOfficialHeaderDto(header: OfficialHeader): OfficialHeaderDto =
        OfficialHeaderDto(
            cycles = header.cycles,
            republic = toLocalizedDto(header.republic)!!,
            motto = toLocalizedDto(header.motto)!!,
            ministry = toLocalizedDto(header.ministry)!!,
            regionalDelegation = toLocalizedDto(header.regionalDelegation),
            divisionalDelegation = toLocalizedDto(header.divisionalDelegation)
        )

    fun toSchoolResponse(school: School): SchoolResponse =
        SchoolResponse(
            id = school.id!!,
            code = school.code,
            name = toLocalizedDto(school.name)!!,
            shortName = school.shortName,
            officialHeaders = school.officialHeaders.map { toOfficialHeaderDto(it) },
            contact = toContactDto(school.contact),
            postalBox = school.postalBox,
            signaturePlace = school.signaturePlace,
            headTitle = toLocalizedDto(school.headTitle),
            headOfSchoolStaffId = school.headOfSchoolStaffId,
            logoFileId = school.logoFileId,
            hasLogo = !school.logoFileId.isNullOrBlank(),
            reportCardNotice = toLocalizedDto(school.reportCardNotice),
            reportCardDisclaimer = toLocalizedDto(school.reportCardDisclaimer),
            timezone = school.timezone,
            active = school.active
        )

    fun toTerm(dto: TermDto): Term =
        Term(
            number = dto.number,
            name = toLocalizedRequired(dto.name),
            startDate = dto.startDate,
            endDate = dto.endDate
        )

    fun toTermDto(term: Term): TermDto =
        TermDto(
            number = term.number,
            name = toLocalizedDto(term.name)!!,
            startDate = term.startDate,
            endDate = term.endDate
        )

    fun toYearResponse(year: AcademicYear, today: LocalDate): AcademicYearResponse =
        AcademicYearResponse(
            id = year.id!!,
            schoolId = year.schoolId,
            label = year.label,
            startDate = year.startDate,
            endDate = year.endDate,
            current = year.current,
            status = year.status,
            locked = yearIsLocked(year, today),
            terms = year.terms.map { toTermDto(it) }
        )

    fun toYearSummary(year: AcademicYear, today: LocalDate): AcademicYearSummaryDto =
        AcademicYearSummaryDto(
            year.id!!,
            year.schoolId,
            year.label,
            year.current,
            year.status,
            yearIsLocked(year, today)
        )

    /**
     * Verrouillage métier : statut terminal OU dernier jour de l’année atteint
     * (`today >= endDate` → plus aucune écriture, y compris pour le directeur).
     */
    fun yearIsLocked(year: AcademicYear, today: LocalDate): Boolean =
        year.status == AcademicYearStatus.CLOSED ||
            year.status == AcademicYearStatus.ARCHIVED ||
            !today.isBefore(year.endDate)

    fun toLevelResponse(level: Level): LevelResponse =
        LevelResponse(
            id = level.id!!,
            schoolId = level.schoolId,
            code = level.code,
            name = toLocalizedDto(level.name)!!,
            cycle = level.cycle,
            section = level.section,
            order = level.order,
            equivalenceIndex = level.equivalenceIndex,
            nextLevelCode = level.nextLevelCode,
            defaultGradingSchemeId = level.defaultGradingSchemeId,
            active = level.active
        )

    fun toLevelSummary(level: Level): LevelSummaryDto =
        LevelSummaryDto(
            id = level.id!!,
            schoolId = level.schoolId,
            code = level.code,
            name = toLocalizedDto(level.name)!!,
            cycle = level.cycle,
            section = level.section,
            active = level.active
        )

    fun toClassResponse(schoolClass: SchoolClass): SchoolClassResponse =
        SchoolClassResponse(
            id = schoolClass.id!!,
            schoolId = schoolClass.schoolId,
            academicYearId = schoolClass.academicYearId,
            levelId = schoolClass.levelId,
            levelCode = schoolClass.levelCode,
            section = schoolClass.section,
            cycle = schoolClass.cycle,
            name = schoolClass.name,
            trackCode = schoolClass.trackCode,
            capacity = schoolClass.capacity,
            mainTeacherId = schoolClass.mainTeacherId,
            homeRoomId = schoolClass.homeRoomId,
            defaultLanguage = schoolClass.defaultLanguage,
            availableLanguages = schoolClass.availableLanguages,
            gradingSchemeId = schoolClass.gradingSchemeId,
            active = schoolClass.active
        )

    fun toClassSummary(schoolClass: SchoolClass): SchoolClassSummaryDto =
        SchoolClassSummaryDto(
            id = schoolClass.id!!,
            schoolId = schoolClass.schoolId,
            academicYearId = schoolClass.academicYearId,
            levelId = schoolClass.levelId,
            levelCode = schoolClass.levelCode,
            name = schoolClass.name,
            section = schoolClass.section,
            defaultLanguage = schoolClass.defaultLanguage,
            gradingSchemeId = schoolClass.gradingSchemeId,
            active = schoolClass.active
        )

    fun toRoomResponse(room: Room): RoomResponse =
        RoomResponse(room.id!!, room.schoolId, room.name, room.type, room.capacity, room.active)

    fun toSubjectResponse(subject: Subject): SubjectResponse =
        SubjectResponse(
            id = subject.id!!,
            schoolId = subject.schoolId,
            code = subject.code,
            name = toLocalizedDto(subject.name)!!,
            shortName = toLocalizedDto(subject.shortName),
            timetableAlias = subject.timetableAlias,
            kind = subject.kind,
            teachingLanguage = subject.teachingLanguage,
            active = subject.active
        )

    fun toSubjectSummary(subject: Subject): SubjectSummaryDto =
        SubjectSummaryDto(
            id = subject.id!!,
            schoolId = subject.schoolId,
            code = subject.code,
            name = toLocalizedDto(subject.name)!!,
            kind = subject.kind,
            active = subject.active
        )

    fun toGroupResponse(group: SubjectGroup): SubjectGroupResponse =
        SubjectGroupResponse(
            id = group.id!!,
            schoolId = group.schoolId,
            code = group.code,
            name = toLocalizedDto(group.name)!!,
            displayOrder = group.displayOrder,
            active = group.active
        )

    fun toClassSubjectResponse(cs: ClassSubject): ClassSubjectResponse =
        ClassSubjectResponse(
            id = cs.id!!,
            schoolId = cs.schoolId,
            academicYearId = cs.academicYearId,
            classId = cs.classId,
            subjectId = cs.subjectId,
            groupId = cs.groupId,
            coefficient = cs.coefficient,
            teacherId = cs.teacherId,
            weeklyHours = cs.weeklyHours,
            teachingLanguage = cs.teachingLanguage,
            displayOrder = cs.displayOrder,
            maxScore = cs.maxScore,
            countsInAverage = cs.countsInAverage,
            optional = cs.optional,
            active = cs.active
        )

    fun toClassSubjectSummary(cs: ClassSubject): ClassSubjectSummaryDto =
        ClassSubjectSummaryDto(
            id = cs.id!!,
            schoolId = cs.schoolId,
            academicYearId = cs.academicYearId,
            classId = cs.classId,
            subjectId = cs.subjectId,
            groupId = cs.groupId,
            coefficient = cs.coefficient,
            teacherId = cs.teacherId,
            weeklyHours = cs.weeklyHours,
            displayOrder = cs.displayOrder,
            maxScore = cs.maxScore,
            countsInAverage = cs.countsInAverage,
            active = cs.active
        )

    fun toGroupSummary(group: SubjectGroup): SubjectGroupSummaryDto =
        SubjectGroupSummaryDto(
            id = group.id!!,
            schoolId = group.schoolId,
            code = group.code,
            name = toLocalizedDto(group.name)!!,
            displayOrder = group.displayOrder,
            active = group.active
        )

    fun toTermCompetencyResponse(item: TermCompetency): TermCompetencyResponse =
        TermCompetencyResponse(
            id = item.id!!,
            schoolId = item.schoolId,
            academicYearId = item.academicYearId,
            classId = item.classId,
            classSubjectId = item.classSubjectId,
            termNumber = item.termNumber,
            text = toLocalizedDto(item.text)!!,
            authorStaffId = item.authorStaffId
        )
}
