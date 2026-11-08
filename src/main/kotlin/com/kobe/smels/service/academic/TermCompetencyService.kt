package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.TermCompetencyResponse
import com.kobe.smels.dto.academic.UpsertTermCompetencyRequest
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.TermCompetency
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.TermCompetencyRepository
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TermCompetencyService(
    private val termCompetencyRepository: TermCompetencyRepository,
    private val classSubjectRepository: ClassSubjectRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(currentUser: CurrentUser, request: UpsertTermCompetencyRequest): Mono<TermCompetencyResponse> {
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        log.info(
            "termCompetency.upsert start by={} schoolId={} termNumber={}",
            currentUser.username,
            schoolId,
            request.termNumber
        )
        return classSubjectRepository.findByIdAndSchoolId(request.classSubjectId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .flatMap { classSubject ->
                assertCanWriteCompetency(currentUser, classSubject.teacherId)
                termCompetencyRepository.findBySchoolIdAndClassSubjectIdAndTermNumber(
                    schoolId,
                    request.classSubjectId,
                    request.termNumber
                )
                    .flatMap { existing ->
                        existing.text = AcademicMapper.toLocalizedRequired(request.text)
                        existing.authorStaffId = currentUser.profileId.takeIf {
                            currentUser.profileType == ProfileType.STAFF
                        }
                        termCompetencyRepository.save(existing)
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            termCompetencyRepository.save(
                                TermCompetency(
                                    schoolId = schoolId,
                                    academicYearId = classSubject.academicYearId,
                                    classId = classSubject.classId,
                                    classSubjectId = classSubject.id!!,
                                    termNumber = request.termNumber,
                                    text = AcademicMapper.toLocalizedRequired(request.text),
                                    authorStaffId = currentUser.profileId.takeIf {
                                        currentUser.profileType == ProfileType.STAFF
                                    }
                                )
                            )
                        }
                    )
            }
            .map { AcademicMapper.toTermCompetencyResponse(it) }
    }

    fun listByClassSubject(
        currentUser: CurrentUser,
        classSubjectId: String,
        schoolIdOverride: String? = null
    ): Flux<TermCompetencyResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return termCompetencyRepository.findBySchoolIdAndClassSubjectId(schoolId, classSubjectId)
            .map { AcademicMapper.toTermCompetencyResponse(it) }
    }

    private fun assertCanWriteCompetency(currentUser: CurrentUser, teacherId: String?) {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) return
        if (currentUser.hasRole(Role.TEACHER) &&
            currentUser.profileType == ProfileType.STAFF &&
            currentUser.profileId != null &&
            currentUser.profileId == teacherId
        ) {
            return
        }
        throw ForbiddenException("FORBIDDEN", "Cannot edit term competency for this class subject")
    }
}
