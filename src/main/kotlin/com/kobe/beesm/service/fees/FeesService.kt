package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.fees.FeePaymentResponse
import com.kobe.beesm.dto.fees.FeesAccessResponse
import com.kobe.beesm.dto.fees.InstallmentSolvencyDto
import com.kobe.beesm.dto.fees.RecordFeePaymentRequest
import com.kobe.beesm.dto.fees.SolvencySnapshotDto
import com.kobe.beesm.dto.fees.StudentFeeAccountResponse
import com.kobe.beesm.dto.fees.TuitionInstallmentResponse
import com.kobe.beesm.dto.fees.TuitionScheduleResponse
import com.kobe.beesm.dto.fees.UpsertTuitionScheduleRequest
import com.kobe.beesm.dto.fees.VoidFeePaymentRequest
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.SolvencyStatus
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.staff.StaffFunction
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.SchoolClassApi
import com.kobe.beesm.service.fees.api.FeesApi
import com.kobe.beesm.service.staff.api.StaffApi
import com.kobe.beesm.service.student.api.EnrollmentApi
import com.kobe.beesm.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Module `fees` : barèmes, paiements, solvabilité (docs/10, BR-F01…F09).
 *
 * BR-F09 : saisie des paiements (reçu banque) = `DIRECTOR` / `SUPER_ADMIN`
 * ou `ADMINISTRATION` avec fonction RH `ACCOUNTANT` ; barèmes = Direction seule.
 */
@Service
class FeesService(
    private val scheduleRepository: TuitionScheduleRepository,
    private val paymentRepository: FeePaymentRepository,
    private val academicYearApi: AcademicYearApi,
    private val schoolClassApi: SchoolClassApi,
    private val enrollmentApi: EnrollmentApi,
    private val staffApi: StaffApi
) : FeesApi {

    private val zone: ZoneId = ZoneId.of("Africa/Douala")

    fun access(currentUser: CurrentUser, schoolIdOverride: String? = null): Mono<FeesAccessResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return resolveAccess(currentUser, schoolId)
    }

    fun listSchedules(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Flux<TuitionScheduleResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return resolveAccess(currentUser, schoolId).flatMapMany { access ->
            if (!access.canManageSchedules && !access.canRecordPayments) {
                return@flatMapMany Flux.error(
                    ForbiddenException(
                        "FEES_SCHEDULE_FORBIDDEN",
                        "Tuition schedules require DIRECTOR or accountant access"
                    )
                )
            }
            scheduleRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
                .map { toScheduleResponse(it) }
        }
    }

    fun upsertSchedule(
        currentUser: CurrentUser,
        request: UpsertTuitionScheduleRequest
    ): Mono<TuitionScheduleResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        validateInstallmentIndexes(request)

        return assertCanManageSchedules(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .flatMap { year ->
                schoolClassApi.getSummary(schoolId, request.classId)
                    .flatMap { clazz ->
                        if (clazz.academicYearId != request.academicYearId) {
                            return@flatMap Mono.error(
                                BusinessRuleException(
                                    "CLASS_YEAR_MISMATCH",
                                    "Class does not belong to the given academic year"
                                )
                            )
                        }
                        val dueDates = SolvencyCalculator.installmentDueDates(year.startDate)
                        val installmentDocs = request.installments
                            .sortedBy { it.index }
                            .map { dto ->
                                TuitionInstallment(
                                    index = dto.index,
                                    amount = dto.amount,
                                    dueDate = dueDates[dto.index - 1]
                                )
                            }
                            .toMutableList()

                        scheduleRepository
                            .findBySchoolIdAndAcademicYearIdAndClassId(
                                schoolId,
                                request.academicYearId,
                                request.classId
                            )
                            .flatMap { existing ->
                                existing.classLabel = clazz.name
                                existing.enrollmentFee = request.enrollmentFee
                                existing.installments = installmentDocs
                                existing.currency = request.currency?.trim()?.ifBlank { "XAF" } ?: "XAF"
                                existing.active = true
                                scheduleRepository.save(existing)
                            }
                            .switchIfEmpty(
                                Mono.defer {
                                    scheduleRepository.save(
                                        TuitionSchedule(
                                            schoolId = schoolId,
                                            academicYearId = request.academicYearId,
                                            classId = request.classId,
                                            classLabel = clazz.name,
                                            enrollmentFee = request.enrollmentFee,
                                            installments = installmentDocs,
                                            currency = request.currency?.trim()?.ifBlank { "XAF" } ?: "XAF",
                                            active = true
                                        )
                                    )
                                }
                            )
                    }
            }
            .map { toScheduleResponse(it) }
    }

    fun recordPayment(
        currentUser: CurrentUser,
        request: RecordFeePaymentRequest
    ): Mono<FeePaymentResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        if (request.kind == FeePaymentKind.INSTALLMENT && request.installmentIndex == null) {
            return Mono.error(
                BusinessRuleException("INSTALLMENT_INDEX_REQUIRED", "installmentIndex is required for INSTALLMENT")
            )
        }
        if (request.kind == FeePaymentKind.ENROLLMENT && request.installmentIndex != null) {
            return Mono.error(
                BusinessRuleException("INSTALLMENT_INDEX_FORBIDDEN", "installmentIndex must be null for ENROLLMENT")
            )
        }

        return assertCanRecordPayments(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .then(
                enrollmentApi.findByStudentAndYear(schoolId, request.studentId, request.academicYearId)
            )
            .flatMap { enrollment ->
                scheduleRepository
                    .findBySchoolIdAndAcademicYearIdAndClassId(
                        schoolId,
                        request.academicYearId,
                        enrollment.classId
                    )
                    .switchIfEmpty(
                        Mono.error(
                            NotFoundException(
                                "TUITION_SCHEDULE_NOT_FOUND",
                                "No tuition schedule for this class"
                            )
                        )
                    )
                    .flatMap { schedule ->
                        if (request.kind == FeePaymentKind.INSTALLMENT) {
                            val idx = request.installmentIndex!!
                            if (schedule.installments.none { it.index == idx }) {
                                return@flatMap Mono.error(
                                    BusinessRuleException(
                                        "INVALID_INSTALLMENT_INDEX",
                                        "Installment index $idx not in schedule"
                                    )
                                )
                            }
                        }
                        paymentRepository.save(
                            FeePayment(
                                schoolId = schoolId,
                                academicYearId = request.academicYearId,
                                studentId = request.studentId,
                                enrollmentId = enrollment.id,
                                classId = enrollment.classId,
                                scheduleId = schedule.id!!,
                                kind = request.kind,
                                installmentIndex = request.installmentIndex,
                                amount = request.amount,
                                paidAt = request.paidAt ?: Instant.now(),
                                method = request.method,
                                reference = request.reference?.trim()?.ifBlank { null },
                                recordedByAccountId = currentUser.accountId,
                                note = request.note?.trim()?.ifBlank { null },
                                status = FeePaymentStatus.RECORDED
                            )
                        )
                    }
            }
            .map { toPaymentResponse(it) }
    }

    fun voidPayment(
        currentUser: CurrentUser,
        paymentId: String,
        request: VoidFeePaymentRequest
    ): Mono<FeePaymentResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        val reason = request.reason.trim()
        if (reason.isBlank()) {
            return Mono.error(BusinessRuleException("VOID_REASON_REQUIRED", "Void reason is required"))
        }
        return assertCanRecordPayments(currentUser, schoolId)
            .then(paymentRepository.findByIdAndSchoolId(paymentId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("FEE_PAYMENT_NOT_FOUND", "Payment not found")))
            .flatMap { payment ->
                if (payment.status == FeePaymentStatus.VOID) {
                    return@flatMap Mono.error(
                        BusinessRuleException("PAYMENT_ALREADY_VOID", "Payment is already void")
                    )
                }
                academicYearApi.assertWritable(schoolId, payment.academicYearId).then(
                    Mono.defer {
                        payment.status = FeePaymentStatus.VOID
                        payment.voidedAt = Instant.now()
                        payment.voidedByAccountId = currentUser.accountId
                        payment.voidReason = reason
                        paymentRepository.save(payment)
                    }
                )
            }
            .map { toPaymentResponse(it) }
    }

    fun getStudentAccount(
        currentUser: CurrentUser,
        studentId: String,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Mono<StudentFeeAccountResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanReadFees(currentUser, schoolId)
            .then(buildAccount(schoolId, studentId, academicYearId))
    }

    fun solvencyForStudents(
        currentUser: CurrentUser,
        academicYearId: String,
        studentIds: List<String>,
        schoolIdOverride: String? = null
    ): Flux<SolvencySnapshotDto> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanReadFees(currentUser, schoolId)
            .thenMany(solvencySnapshot(schoolId, academicYearId, studentIds))
    }

    override fun solvencySnapshot(
        schoolId: String,
        academicYearId: String,
        studentIds: Collection<String>
    ): Flux<SolvencySnapshotDto> {
        val ids = studentIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            return Flux.empty()
        }
        val today = LocalDate.now(zone)
        return Flux.fromIterable(ids)
            .flatMap({ studentId ->
                computeSolvency(schoolId, studentId, academicYearId, today)
                    .map { SolvencySnapshotDto(studentId = studentId, status = it.status) }
            }, 16)
    }

    private fun buildAccount(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<StudentFeeAccountResponse> {
        val today = LocalDate.now(zone)
        return computeSolvency(schoolId, studentId, academicYearId, today)
            .flatMap { solvency ->
                paymentRepository
                    .findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                        schoolId,
                        academicYearId,
                        studentId,
                        FeePaymentStatus.RECORDED
                    )
                    .map { toPaymentResponse(it) }
                    .collectList()
                    .map { payments ->
                        StudentFeeAccountResponse(
                            studentId = studentId,
                            academicYearId = academicYearId,
                            enrollmentId = solvency.enrollmentId,
                            classId = solvency.classId,
                            classLabel = solvency.classLabel,
                            scheduleId = solvency.scheduleId,
                            currency = solvency.currency,
                            solvency = solvency.status,
                            enrollmentFeeDue = solvency.enrollmentFeeDue,
                            enrollmentFeePaid = solvency.enrollmentFeePaid,
                            installments = solvency.installments,
                            payments = payments
                        )
                    }
            }
    }

    private data class SolvencyInternal(
        val status: SolvencyStatus,
        val enrollmentId: String?,
        val classId: String?,
        val classLabel: String?,
        val scheduleId: String?,
        val currency: String?,
        val enrollmentFeeDue: BigDecimal?,
        val enrollmentFeePaid: BigDecimal?,
        val installments: List<InstallmentSolvencyDto>
    )

    private fun computeSolvency(
        schoolId: String,
        studentId: String,
        academicYearId: String,
        today: LocalDate
    ): Mono<SolvencyInternal> =
        enrollmentApi.findByStudentAndYear(schoolId, studentId, academicYearId)
            .onErrorResume(NotFoundException::class.java) { Mono.empty() }
            .flatMap { enrollment ->
                scheduleRepository
                    .findBySchoolIdAndAcademicYearIdAndClassId(
                        schoolId,
                        academicYearId,
                        enrollment.classId
                    )
                    .flatMap { schedule ->
                        paymentRepository
                            .findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                                schoolId,
                                academicYearId,
                                studentId,
                                FeePaymentStatus.RECORDED
                            )
                            .collectList()
                            .map { payments ->
                                val enrollmentPaid = sumPayments(
                                    payments,
                                    FeePaymentKind.ENROLLMENT,
                                    null
                                )
                                val installmentDtos = schedule.installments
                                    .sortedBy { it.index }
                                    .map { inst ->
                                        val paid = sumPayments(
                                            payments,
                                            FeePaymentKind.INSTALLMENT,
                                            inst.index
                                        )
                                        InstallmentSolvencyDto(
                                            index = inst.index,
                                            amount = inst.amount,
                                            dueDate = inst.dueDate,
                                            paidAmount = paid,
                                            settled = SolvencyCalculator.isSettled(inst.amount, paid)
                                        )
                                    }
                                val status = SolvencyCalculator.compute(
                                    today = today,
                                    enrollmentFeeDue = schedule.enrollmentFee,
                                    enrollmentFeePaid = enrollmentPaid,
                                    installments = installmentDtos.map {
                                        SolvencyCalculator.InstallmentInput(
                                            index = it.index,
                                            amount = it.amount,
                                            dueDate = it.dueDate,
                                            paidAmount = it.paidAmount
                                        )
                                    }
                                )
                                SolvencyInternal(
                                    status = status,
                                    enrollmentId = enrollment.id,
                                    classId = enrollment.classId,
                                    classLabel = schedule.classLabel,
                                    scheduleId = schedule.id,
                                    currency = schedule.currency,
                                    enrollmentFeeDue = schedule.enrollmentFee,
                                    enrollmentFeePaid = enrollmentPaid,
                                    installments = installmentDtos
                                )
                            }
                    }
                    .switchIfEmpty(
                        Mono.just(
                            SolvencyInternal(
                                status = SolvencyStatus.NOT_APPLICABLE,
                                enrollmentId = enrollment.id,
                                classId = enrollment.classId,
                                classLabel = null,
                                scheduleId = null,
                                currency = null,
                                enrollmentFeeDue = null,
                                enrollmentFeePaid = null,
                                installments = emptyList()
                            )
                        )
                    )
            }
            .defaultIfEmpty(
                SolvencyInternal(
                    status = SolvencyStatus.NOT_APPLICABLE,
                    enrollmentId = null,
                    classId = null,
                    classLabel = null,
                    scheduleId = null,
                    currency = null,
                    enrollmentFeeDue = null,
                    enrollmentFeePaid = null,
                    installments = emptyList()
                )
            )

    private fun sumPayments(
        payments: List<FeePayment>,
        kind: FeePaymentKind,
        installmentIndex: Int?
    ): BigDecimal =
        payments
            .filter { it.kind == kind && it.installmentIndex == installmentIndex }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

    private fun validateInstallmentIndexes(request: UpsertTuitionScheduleRequest) {
        val indexes = request.installments.map { it.index }.sorted()
        if (indexes != listOf(1, 2, 3)) {
            throw BusinessRuleException(
                "INVALID_INSTALLMENTS",
                "Exactly three installments with indexes 1, 2 and 3 are required"
            )
        }
    }

    private fun resolveAccess(currentUser: CurrentUser, schoolId: String): Mono<FeesAccessResponse> {
        val canRead = currentUser.hasAnyRole(
            Role.ADMINISTRATION,
            Role.DIRECTOR,
            Role.SUPER_ADMIN,
            Role.SURVEILLANT_GENERAL,
            Role.TEACHER
        )
        val canManageSchedules = currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)
        if (canManageSchedules) {
            return Mono.just(
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = true,
                    canRecordPayments = true
                )
            )
        }
        if (!currentUser.hasRole(Role.ADMINISTRATION)) {
            return Mono.just(
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = false,
                    canRecordPayments = false
                )
            )
        }
        return isAccountantStaff(currentUser, schoolId)
            .map { accountant ->
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = false,
                    canRecordPayments = accountant
                )
            }
    }

    private fun isAccountantStaff(currentUser: CurrentUser, schoolId: String): Mono<Boolean> {
        if (currentUser.profileType != ProfileType.STAFF) {
            return Mono.just(false)
        }
        val staffId = currentUser.profileId?.trim().orEmpty()
        if (staffId.isEmpty()) {
            return Mono.just(false)
        }
        return staffApi.getSummary(schoolId, staffId)
            .map { summary -> summary.functions.contains(StaffFunction.ACCOUNTANT) }
            .onErrorReturn(false)
            .defaultIfEmpty(false)
    }

    private fun assertCanManageSchedules(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        resolveAccess(currentUser, schoolId).flatMap { access ->
            if (access.canManageSchedules) Mono.empty()
            else Mono.error(
                ForbiddenException(
                    "FEES_SCHEDULE_FORBIDDEN",
                    "Only DIRECTOR can manage tuition schedules"
                )
            )
        }

    private fun assertCanRecordPayments(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        resolveAccess(currentUser, schoolId).flatMap { access ->
            if (access.canRecordPayments) Mono.empty()
            else Mono.error(
                ForbiddenException(
                    "FEES_PAYMENT_FORBIDDEN",
                    "Recording fee payments requires DIRECTOR or ADMINISTRATION with ACCOUNTANT function"
                )
            )
        }

    private fun assertCanReadFees(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        resolveAccess(currentUser, schoolId).flatMap { access ->
            if (access.canRead) Mono.empty()
            else Mono.error(ForbiddenException("FORBIDDEN", "Fees read access denied"))
        }

    private fun toScheduleResponse(schedule: TuitionSchedule): TuitionScheduleResponse =
        TuitionScheduleResponse(
            id = schedule.id!!,
            schoolId = schedule.schoolId,
            academicYearId = schedule.academicYearId,
            classId = schedule.classId,
            classLabel = schedule.classLabel,
            enrollmentFee = schedule.enrollmentFee,
            installments = schedule.installments
                .sortedBy { it.index }
                .map {
                    TuitionInstallmentResponse(
                        index = it.index,
                        amount = it.amount,
                        dueDate = it.dueDate
                    )
                },
            currency = schedule.currency,
            active = schedule.active
        )

    private fun toPaymentResponse(payment: FeePayment): FeePaymentResponse =
        FeePaymentResponse(
            id = payment.id!!,
            schoolId = payment.schoolId,
            academicYearId = payment.academicYearId,
            studentId = payment.studentId,
            enrollmentId = payment.enrollmentId,
            classId = payment.classId,
            scheduleId = payment.scheduleId,
            kind = payment.kind,
            installmentIndex = payment.installmentIndex,
            amount = payment.amount,
            paidAt = payment.paidAt,
            method = payment.method,
            reference = payment.reference,
            recordedByAccountId = payment.recordedByAccountId,
            note = payment.note,
            status = payment.status,
            voidedAt = payment.voidedAt,
            voidReason = payment.voidReason
        )

}
