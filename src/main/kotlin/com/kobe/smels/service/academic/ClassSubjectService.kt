package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.ClassSubjectResponse
import com.kobe.smels.dto.academic.ClassSubjectSummaryDto
import com.kobe.smels.dto.academic.CreateClassSubjectRequest
import com.kobe.smels.dto.academic.LocalizedTextDto
import com.kobe.smels.dto.academic.PageResponse
import com.kobe.smels.dto.academic.TeacherAssignmentResponse
import com.kobe.smels.dto.academic.UpdateClassSubjectRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SubjectGroupRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import kotlin.math.ceil

@Service
class ClassSubjectService(
    private val classSubjectRepository: ClassSubjectRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val subjectGroupRepository: SubjectGroupRepository,
    private val academicYearApi: AcademicYearApi,
    private val staffApi: StaffApi,
    private val outboxWriter: OutboxWriter
) : ClassSubjectApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateClassSubjectRequest): Mono<ClassSubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        AcademicAccess.logSuperAdmin(currentUser, schoolId, "classSubject.create")
        validateScores(request.coefficient, request.maxScore)
        log.info(
            "classSubject.create start by={} schoolId={} classId={} subjectId={}",
            currentUser.username,
            schoolId,
            request.classId,
            request.subjectId
        )

        return academicYearApi.assertWritable(schoolId, request.academicYearId)
            .then(schoolClassRepository.findByIdAndSchoolId(request.classId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_NOT_FOUND", "Class not found")))
            .flatMap { schoolClass ->
                if (schoolClass.academicYearId != request.academicYearId) {
                    Mono.error(BusinessRuleException("YEAR_MISMATCH", "Class does not belong to academic year"))
                } else {
                    Mono.just(schoolClass)
                }
            }
            .flatMap {
                subjectRepository.findByIdAndSchoolId(request.subjectId, schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            }
            .flatMap {
                subjectGroupRepository.findByIdAndSchoolId(request.groupId, schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            }
            .flatMap { ensureUnique(schoolId, request.classId, request.subjectId).thenReturn(it) }
            .flatMap { validateTeacher(schoolId, request.teacherId).thenReturn(it) }
            .flatMap {
                classSubjectRepository.save(
                    ClassSubject(
                        schoolId = schoolId,
                        academicYearId = request.academicYearId,
                        classId = request.classId,
                        subjectId = request.subjectId,
                        groupId = request.groupId,
                        coefficient = request.coefficient,
                        teacherId = request.teacherId?.trim()?.ifBlank { null },
                        weeklyHours = request.weeklyHours,
                        teachingLanguage = request.teachingLanguage,
                        displayOrder = request.displayOrder,
                        maxScore = request.maxScore,
                        countsInAverage = request.countsInAverage,
                        optional = request.optional,
                        active = true
                    )
                )
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(AcademicMapper.toClassSubjectResponse(saved)) }
            .doOnNext {
                log.info("classSubject.create ok by={} schoolId={} id={}", currentUser.username, schoolId, it.id)
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateClassSubjectRequest,
        schoolIdOverride: String? = null
    ): Mono<ClassSubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        validateScores(request.coefficient, request.maxScore)
        return classSubjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .flatMap { cs ->
                academicYearApi.assertWritable(schoolId, cs.academicYearId)
                    .then(subjectGroupRepository.findByIdAndSchoolId(request.groupId, schoolId))
                    .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
                    .then(validateTeacher(schoolId, request.teacherId))
                    .then(Mono.fromCallable {
                        cs.groupId = request.groupId
                        cs.coefficient = request.coefficient
                        cs.teacherId = request.teacherId?.trim()?.ifBlank { null }
                        cs.weeklyHours = request.weeklyHours
                        cs.teachingLanguage = request.teachingLanguage
                        cs.displayOrder = request.displayOrder
                        cs.maxScore = request.maxScore
                        cs.countsInAverage = request.countsInAverage
                        cs.optional = request.optional
                        cs
                    })
            }
            .flatMap { classSubjectRepository.save(it) }
            .flatMap { saved -> emitChanged(saved).thenReturn(AcademicMapper.toClassSubjectResponse(saved)) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<ClassSubjectResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return classSubjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .map { AcademicMapper.toClassSubjectResponse(it) }
    }

    /**
     * Affectations d’un enseignant (ClassSubject.teacherId), avec libellés classe / matière.
     * [academicYearId] optionnel : toutes les années si null.
     */
    fun listAssignmentsForTeacher(
        currentUser: CurrentUser,
        teacherId: String,
        academicYearId: String? = null,
        schoolIdOverride: String? = null
    ): Mono<List<TeacherAssignmentResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val tid = teacherId.trim()
        if (tid.isEmpty()) {
            return Mono.error(BusinessRuleException("TEACHER_ID_REQUIRED", "teacherId is required"))
        }
        AcademicAccess.logSuperAdmin(currentUser, schoolId, "classSubject.listByTeacher")

        val source = if (academicYearId.isNullOrBlank()) {
            classSubjectRepository.findBySchoolIdAndTeacherId(schoolId, tid)
        } else {
            classSubjectRepository.findBySchoolIdAndTeacherIdAndAcademicYearId(
                schoolId, tid, academicYearId.trim()
            )
        }

        return source
            .filter { it.active }
            .concatMap { cs -> toTeacherAssignment(cs) }
            .collectList()
            .map { list ->
                list.sortedWith(
                    compareBy({ it.className.lowercase() }, { it.subjectCode.lowercase() })
                )
            }
    }

    fun listByClass(
        currentUser: CurrentUser,
        classId: String,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<ClassSubjectResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("displayOrder").ascending())
        val source = if (active != null) {
            classSubjectRepository.findBySchoolIdAndClassIdAndActive(schoolId, classId, active, pageable)
        } else {
            classSubjectRepository.findBySchoolIdAndClassId(schoolId, classId, pageable)
        }
        val total = if (active != null) {
            classSubjectRepository.countBySchoolIdAndClassIdAndActive(schoolId, classId, active)
        } else {
            classSubjectRepository.countBySchoolIdAndClassId(schoolId, classId)
        }
        return source.map { AcademicMapper.toClassSubjectResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(
                t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt()
            )
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<ClassSubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return classSubjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .flatMap { cs ->
                cs.active = false
                classSubjectRepository.save(cs)
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(AcademicMapper.toClassSubjectResponse(saved)) }
    }

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<ClassSubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return classSubjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .flatMap { cs ->
                cs.active = true
                classSubjectRepository.save(cs)
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(AcademicMapper.toClassSubjectResponse(saved)) }
    }

    override fun getSummary(schoolId: String, classSubjectId: String): Mono<ClassSubjectSummaryDto> =
        classSubjectRepository.findByIdAndSchoolId(classSubjectId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .map { AcademicMapper.toClassSubjectSummary(it) }

    override fun findByClass(schoolId: String, classId: String): Flux<ClassSubjectSummaryDto> =
        classSubjectRepository.findBySchoolIdAndClassId(schoolId, classId)
            .map { AcademicMapper.toClassSubjectSummary(it) }

    override fun findByTeacher(
        schoolId: String,
        teacherId: String,
        academicYearId: String?
    ): Flux<ClassSubjectSummaryDto> {
        val source = if (academicYearId.isNullOrBlank()) {
            classSubjectRepository.findBySchoolIdAndTeacherId(schoolId, teacherId)
        } else {
            classSubjectRepository.findBySchoolIdAndTeacherIdAndAcademicYearId(
                schoolId, teacherId, academicYearId
            )
        }
        return source.map { AcademicMapper.toClassSubjectSummary(it) }
    }

    private fun toTeacherAssignment(cs: ClassSubject): Mono<TeacherAssignmentResponse> {
        val classMono = schoolClassRepository.findByIdAndSchoolId(cs.classId, cs.schoolId)
            .map { it.name }
            .defaultIfEmpty("—")
        val subjectMono = subjectRepository.findByIdAndSchoolId(cs.subjectId, cs.schoolId)
            .map { subject ->
                Triple(
                    subject.code,
                    AcademicMapper.toLocalizedDto(subject.name) ?: LocalizedTextDto(),
                    subject.id!!
                )
            }
            .defaultIfEmpty(Triple("—", LocalizedTextDto(fr = "—", en = "—"), cs.subjectId))

        return Mono.zip(classMono, subjectMono).map { tuple ->
            val className = tuple.t1
            val (code, name, subjectId) = tuple.t2
            TeacherAssignmentResponse(
                classSubjectId = cs.id!!,
                schoolId = cs.schoolId,
                academicYearId = cs.academicYearId,
                classId = cs.classId,
                className = className,
                subjectId = subjectId,
                subjectCode = code,
                subjectName = name,
                coefficient = cs.coefficient,
                weeklyHours = cs.weeklyHours,
                active = cs.active
            )
        }
    }

    override fun findActiveByYear(schoolId: String, academicYearId: String): Flux<ClassSubjectSummaryDto> =
        classSubjectRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, academicYearId)
            .map { AcademicMapper.toClassSubjectSummary(it) }

    /** BR-03 */
    private fun ensureUnique(schoolId: String, classId: String, subjectId: String): Mono<Void> =
        classSubjectRepository.findBySchoolIdAndClassIdAndSubjectId(schoolId, classId, subjectId)
            .flatMap<Void> {
                Mono.error(ConflictException("CLASS_SUBJECT_EXISTS", "Subject already assigned to this class"))
            }
            .switchIfEmpty(Mono.empty())

    private fun validateTeacher(schoolId: String, teacherId: String?): Mono<Void> {
        val id = teacherId?.trim()?.ifBlank { null } ?: return Mono.empty()
        return staffApi.getSummary(schoolId, id).then()
    }

    private fun validateScores(coefficient: BigDecimal, maxScore: BigDecimal) {
        if (coefficient.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessRuleException("COEFFICIENT_INVALID", "Coefficient must be strictly positive")
        }
        if (maxScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessRuleException("MAX_SCORE_INVALID", "maxScore must be strictly positive")
        }
    }

    private fun emitChanged(cs: ClassSubject): Mono<Void> =
        outboxWriter.append(
            schoolId = cs.schoolId,
            module = "academic",
            aggregateType = "ClassSubject",
            aggregateId = cs.id!!,
            eventType = "academic.class-subject.changed",
            payload = mapOf(
                "classSubjectId" to cs.id,
                "classId" to cs.classId,
                "subjectId" to cs.subjectId,
                "teacherId" to cs.teacherId,
                "coefficient" to cs.coefficient.toPlainString(),
                "groupId" to cs.groupId,
                "weeklyHours" to cs.weeklyHours,
                "active" to cs.active
            )
        ).then()
}
