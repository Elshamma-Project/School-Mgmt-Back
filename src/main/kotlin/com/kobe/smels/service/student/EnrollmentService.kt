package com.kobe.smels.service.student

import com.kobe.smels.dto.student.CreateEnrollmentRequest
import com.kobe.smels.dto.student.EnrollmentResponse
import com.kobe.smels.dto.student.EnrollmentSummaryDto
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.UpdateEnrollmentRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.student.EnrollmentRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.ceil

@Service
class EnrollmentService(
    private val enrollmentRepository: EnrollmentRepository,
    private val studentRepository: StudentRepository,
    private val academicYearApi: AcademicYearApi,
    private val schoolClassApi: SchoolClassApi,
    private val outboxWriter: OutboxWriter
) : EnrollmentApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateEnrollmentRequest): Mono<EnrollmentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        logSuperAdminTenantAction(currentUser, schoolId, "enrollment.create")
        log.info(
            "enrollment.create start by={} schoolId={} yearId={} classId={}",
            currentUser.username,
            schoolId,
            request.academicYearId,
            request.classId
        )

        return studentRepository.findByIdAndSchoolId(request.studentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                if (!student.active) {
                    Mono.error(ConflictException("STUDENT_INACTIVE", "Cannot enroll an inactive student"))
                } else {
                    academicYearApi.getSummary(schoolId, request.academicYearId)
                        .then(schoolClassApi.getSummary(schoolId, request.classId))
                        .flatMap { schoolClass ->
                            if (schoolClass.academicYearId != request.academicYearId) {
                                Mono.error(
                                    BusinessRuleException(
                                        "CLASS_YEAR_MISMATCH",
                                        "Class does not belong to the given academic year"
                                    )
                                )
                            } else if (schoolClass.levelId != request.levelId) {
                                Mono.error(
                                    BusinessRuleException(
                                        "CLASS_LEVEL_MISMATCH",
                                        "levelId does not match the class level"
                                    )
                                )
                            } else {
                                ensureNoEnrollmentForYear(schoolId, request.studentId, request.academicYearId)
                                    .then(
                                        Mono.fromCallable {
                                            Enrollment(
                                                schoolId = schoolId,
                                                studentId = request.studentId,
                                                academicYearId = request.academicYearId.trim(),
                                                classId = request.classId.trim(),
                                                levelId = request.levelId.trim(),
                                                rollNumber = request.rollNumber,
                                                repeater = request.repeater,
                                                newStudent = request.newStudent,
                                                status = EnrollmentStatus.ENROLLED,
                                                enrolledOn = request.enrolledOn,
                                                previousSchool = request.previousSchool?.trim()?.ifBlank { null }
                                            )
                                        }
                                    )
                            }
                        }
                }
            }
            .flatMap { enrollmentRepository.save(it) }
            .flatMap { saved ->
                emitEnrollmentEvent(saved, "student.enrolled").thenReturn(StudentMapper.toEnrollmentResponse(saved))
            }
            .doOnNext {
                log.info(
                    "enrollment.create ok by={} schoolId={} yearId={} classId={}",
                    currentUser.username,
                    schoolId,
                    request.academicYearId,
                    request.classId
                )
            }
            .doOnError { error ->
                log.warn(
                    "enrollment.create failed by={} schoolId={} reason={}",
                    currentUser.username,
                    schoolId,
                    error.message
                )
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateEnrollmentRequest,
        schoolIdOverride: String? = null
    ): Mono<EnrollmentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.info("enrollment.update start by={} schoolId={}", currentUser.username, schoolId)
        return enrollmentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ENROLLMENT_NOT_FOUND", "Enrollment not found")))
            .flatMap { enrollment ->
                enrollment.classId = request.classId.trim()
                enrollment.levelId = request.levelId.trim()
                enrollment.rollNumber = request.rollNumber
                enrollment.repeater = request.repeater
                enrollment.newStudent = request.newStudent
                enrollment.status = request.status
                enrollment.enrolledOn = request.enrolledOn
                enrollment.leftOn = request.leftOn
                enrollment.previousSchool = request.previousSchool?.trim()?.ifBlank { null }
                enrollmentRepository.save(enrollment)
            }
            .flatMap { saved ->
                emitEnrollmentEvent(saved, "student.enrollment.changed")
                    .thenReturn(StudentMapper.toEnrollmentResponse(saved))
            }
            .doOnNext {
                log.info("enrollment.update ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<EnrollmentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return enrollmentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ENROLLMENT_NOT_FOUND", "Enrollment not found")))
            .map { StudentMapper.toEnrollmentResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        academicYearId: String?,
        classId: String?,
        status: EnrollmentStatus?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<EnrollmentResponse>> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.info(
            "enrollment.list by={} schoolId={} page={} size={} hasYear={} hasClass={} status={}",
            currentUser.username,
            schoolId,
            page,
            size,
            !academicYearId.isNullOrBlank(),
            !classId.isNullOrBlank(),
            status
        )
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("enrolledOn").descending())

        val source: Flux<Enrollment>
        val totalMono: Mono<Long>
        when {
            classId != null && status != null -> {
                source = enrollmentRepository.findBySchoolIdAndClassIdAndStatus(schoolId, classId, status, pageable)
                totalMono = enrollmentRepository.countBySchoolIdAndClassIdAndStatus(schoolId, classId, status)
            }
            academicYearId != null -> {
                source = enrollmentRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId, pageable)
                totalMono = enrollmentRepository.countBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            }
            else -> {
                // Liste large évitée : exiger au moins une année.
                return Mono.error(
                    ForbiddenException("FILTER_REQUIRED", "academicYearId or classId+status filter is required")
                )
            }
        }

        return source.map { StudentMapper.toEnrollmentResponse(it) }
            .collectList()
            .zipWith(totalMono)
            .map { tuple ->
                val content = tuple.t1
                val total = tuple.t2
                PageResponse(
                    content = content,
                    page = pageable.pageNumber,
                    size = pageable.pageSize,
                    totalElements = total,
                    totalPages = if (total == 0L) 0 else ceil(total.toDouble() / pageable.pageSize).toInt()
                )
            }
    }

    fun listByStudent(
        currentUser: CurrentUser,
        studentId: String,
        schoolIdOverride: String? = null
    ): Mono<List<EnrollmentResponse>> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanReadStudentEnrollments(currentUser, schoolId, studentId)
            .thenMany(enrollmentRepository.findBySchoolIdAndStudentId(schoolId, studentId))
            .map { StudentMapper.toEnrollmentResponse(it) }
            .collectList()
    }

    override fun getSummary(schoolId: String, enrollmentId: String): Mono<EnrollmentSummaryDto> =
        enrollmentRepository.findByIdAndSchoolId(enrollmentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ENROLLMENT_NOT_FOUND", "Enrollment not found")))
            .map { StudentMapper.toEnrollmentSummary(it) }

    override fun findByStudentAndYear(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<EnrollmentSummaryDto> =
        enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, studentId, academicYearId)
            .switchIfEmpty(Mono.error(NotFoundException("ENROLLMENT_NOT_FOUND", "Enrollment not found")))
            .map { StudentMapper.toEnrollmentSummary(it) }

    override fun findByStudent(schoolId: String, studentId: String): Flux<EnrollmentSummaryDto> =
        enrollmentRepository.findBySchoolIdAndStudentId(schoolId, studentId)
            .map { StudentMapper.toEnrollmentSummary(it) }

    override fun findEnrolledByClass(schoolId: String, classId: String): Flux<EnrollmentSummaryDto> =
        enrollmentRepository.findBySchoolIdAndClassIdAndStatus(schoolId, classId, EnrollmentStatus.ENROLLED)
            .map { StudentMapper.toEnrollmentSummary(it) }

    override fun isEnrolled(
        schoolId: String,
        studentId: String,
        classId: String,
        academicYearId: String
    ): Mono<Boolean> =
        enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, studentId, academicYearId)
            .map { it.status == EnrollmentStatus.ENROLLED && it.classId == classId }
            .defaultIfEmpty(false)

    /** BR-41 : au plus une inscription par année scolaire. */
    private fun ensureNoEnrollmentForYear(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<Void> =
        enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, studentId, academicYearId)
            .flatMap<Void> {
                Mono.error(
                    ConflictException(
                        "ENROLLMENT_YEAR_EXISTS",
                        "Student already has an enrollment for this academic year"
                    )
                )
            }
            .switchIfEmpty(Mono.empty())

    private fun emitEnrollmentEvent(enrollment: Enrollment, eventType: String): Mono<Void> =
        outboxWriter.append(
            schoolId = enrollment.schoolId,
            module = "student",
            aggregateType = "Enrollment",
            aggregateId = enrollment.id!!,
            eventType = eventType,
            payload = mapOf(
                "enrollmentId" to enrollment.id,
                "studentId" to enrollment.studentId,
                "classId" to enrollment.classId,
                "academicYearId" to enrollment.academicYearId,
                "levelId" to enrollment.levelId,
                "status" to enrollment.status.name
            )
        )
            .doOnNext {
                log.debug("outbox appended eventType={} schoolId={}", eventType, enrollment.schoolId)
            }
            .then()

    private fun assertCanManage(currentUser: CurrentUser) {
        // docs/05 : inscriptions CRUD = ADMIN / DIRECTOR / SUPER_ADMIN uniquement
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage enrollments")
        }
    }

    /** Lecture du parcours (toutes les années) : admin/dir, l'élève, ou un parent lié. */
    private fun assertCanReadStudentEnrollments(
        currentUser: CurrentUser,
        schoolId: String,
        studentId: String
    ): Mono<Void> {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Mono.empty()
        }
        if (currentUser.hasRole(Role.STUDENT) &&
            currentUser.profileType == ProfileType.STUDENT &&
            currentUser.profileId == studentId
        ) {
            return Mono.empty()
        }
        if (currentUser.hasRole(Role.PARENT) &&
            currentUser.profileType == ProfileType.GUARDIAN &&
            currentUser.profileId != null
        ) {
            return studentRepository.findByIdAndSchoolId(studentId, schoolId)
                .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
                .flatMap { student ->
                    val linked = student.guardians.any { it.guardianId == currentUser.profileId }
                    if (linked) Mono.empty()
                    else Mono.error(ForbiddenException("FORBIDDEN", "Guardian is not linked to this student"))
                }
        }
        return Mono.error(ForbiddenException("FORBIDDEN", "Cannot read enrollments for this student"))
    }

    private fun logSuperAdminTenantAction(currentUser: CurrentUser, schoolId: String, action: String) {
        if (currentUser.isSuperAdmin()) {
            log.info(
                "SUPER_ADMIN tenant action action={} by={} targetSchoolId={} tokenSchoolId={}",
                action,
                currentUser.username,
                schoolId,
                currentUser.schoolId
            )
        }
    }
}
