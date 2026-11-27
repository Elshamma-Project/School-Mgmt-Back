package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.academic.LocalizedTextDto
import com.kobe.beesm.dto.fees.DailyAmountDto
import com.kobe.beesm.dto.fees.ExclusionRiskDto
import com.kobe.beesm.dto.fees.ExtraFeeSolvencyDto
import com.kobe.beesm.dto.fees.FeePaymentResponse
import com.kobe.beesm.dto.fees.FeesAccessGateDto
import com.kobe.beesm.dto.fees.FeesAccessResponse
import com.kobe.beesm.dto.fees.FeesDashboardSummaryResponse
import com.kobe.beesm.dto.fees.FeesUnpaidSummaryResponse
import com.kobe.beesm.dto.fees.InstallmentSolvencyDto
import com.kobe.beesm.dto.fees.InstallmentUnpaidCountDto
import com.kobe.beesm.dto.fees.LevelInsolvencyDto
import com.kobe.beesm.dto.fees.PunctualityStatsDto
import com.kobe.beesm.dto.fees.RecentPaymentsDto
import com.kobe.beesm.dto.fees.ReactivateAfterFeesRequest
import com.kobe.beesm.dto.fees.ReactivateAfterFeesResponse
import com.kobe.beesm.dto.fees.RecordFeePaymentRequest
import com.kobe.beesm.dto.fees.SolvencySnapshotDto
import com.kobe.beesm.dto.fees.StudentFeeAccountResponse
import com.kobe.beesm.dto.fees.SyncLevelScheduleFromDefaultsRequest
import com.kobe.beesm.dto.fees.TuitionExtraFeeResponse
import com.kobe.beesm.dto.fees.TuitionInstallmentResponse
import com.kobe.beesm.dto.fees.TuitionScheduleDefaultsResponse
import com.kobe.beesm.dto.fees.TuitionScheduleResponse
import com.kobe.beesm.dto.fees.UpsertTuitionScheduleDefaultsRequest
import com.kobe.beesm.dto.fees.UpsertTuitionScheduleRequest
import com.kobe.beesm.dto.fees.VoidFeePaymentRequest
import com.kobe.beesm.model.fees.FeeStudentFeeState
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.SolvencyStatus
import com.kobe.beesm.model.fees.TuitionExtraFee
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.fees.TuitionScheduleDefaults
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.staff.StaffFunction
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.FeeStudentFeeStateRepository
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.repository.fees.TuitionScheduleDefaultsRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.dto.academic.SchoolBulletinBrandDto
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.LevelApi
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.service.academic.api.SchoolClassApi
import com.kobe.beesm.service.fees.api.FeesApi
import com.kobe.beesm.service.staff.api.StaffApi
import com.kobe.beesm.service.student.api.EnrollmentApi
import com.kobe.beesm.service.student.api.StudentApi
import com.kobe.beesm.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Module `fees` : barèmes, paiements, solvabilité (docs/10, BR-F01…F16).
 *
 * BR-F09 : saisie des paiements (reçu banque) = `DIRECTOR` / `SUPER_ADMIN`
 * ou `ADMINISTRATION` avec fonction RH `ACCOUNTANT` ; barèmes = Direction seule.
 * Lecture solvabilité : admin / dir. / SG ; **parent = ses enfants seulement** ; **pas l’enseignant**.
 */
@Service
class FeesService(
    private val scheduleRepository: TuitionScheduleRepository,
    private val defaultsRepository: TuitionScheduleDefaultsRepository,
    private val paymentRepository: FeePaymentRepository,
    private val stateRepository: FeeStudentFeeStateRepository,
    private val policyRepository: FeesPolicyRepository,
    private val academicYearApi: AcademicYearApi,
    private val levelApi: LevelApi,
    private val schoolClassApi: SchoolClassApi,
    private val schoolBrandApi: SchoolBrandApi,
    private val enrollmentApi: EnrollmentApi,
    private val staffApi: StaffApi,
    private val studentApi: StudentApi,
    private val enrollmentReceiptPdfService: FeeEnrollmentReceiptPdfService
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
    ): Mono<List<TuitionScheduleResponse>> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return resolveAccess(currentUser, schoolId).flatMap { access ->
            if (!access.canManageSchedules && !access.canRecordPayments) {
                return@flatMap Mono.error(
                    ForbiddenException(
                        "FEES_SCHEDULE_FORBIDDEN",
                        "Tuition schedules require DIRECTOR or accountant access"
                    )
                )
            }
            // collectList : JSON complet ; on ignore les barèmes legacy sans levelId (ex-classId).
            scheduleRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
                .concatMap { schedule ->
                    val levelId = schedule.levelId?.trim().orEmpty()
                    if (levelId.isEmpty()) {
                        val id = schedule.id
                        if (id.isNullOrBlank()) {
                            Mono.empty()
                        } else {
                            scheduleRepository.deleteById(id).then(Mono.empty())
                        }
                    } else {
                        Mono.fromCallable { toScheduleResponse(schedule) }
                            .onErrorResume { Mono.empty() }
                    }
                }
                .collectList()
        }
    }

    fun getScheduleDefaults(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Mono<TuitionScheduleDefaultsResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanManageSchedules(currentUser, schoolId)
            .then(
                defaultsRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
                    .map { toDefaultsResponse(it) }
                    .switchIfEmpty(
                        Mono.error(
                            NotFoundException(
                                "SCHEDULE_DEFAULTS_NOT_FOUND",
                                "No schedule defaults for this academic year"
                            )
                        )
                    )
            )
    }

    /**
     * BR-F16 : enregistre le modèle général puis amorce les niveaux listés
     * qui n’ont **pas encore** de barème (les autres restent inchangés).
     */
    fun upsertScheduleDefaults(
        currentUser: CurrentUser,
        request: UpsertTuitionScheduleDefaultsRequest
    ): Mono<TuitionScheduleDefaultsResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        validateDefaultsInstallments(request)

        val installmentDocs = request.installments
            .sortedBy { it.index }
            .map { TuitionInstallment(index = it.index, amount = it.amount, dueDate = it.dueDate) }
            .toMutableList()
        val currency = request.currency?.trim()?.ifBlank { "XAF" } ?: "XAF"
        val seedIds = request.seedLevelIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        return assertCanManageSchedules(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .then(
                defaultsRepository.findBySchoolIdAndAcademicYearId(schoolId, request.academicYearId)
                    .flatMap { existing ->
                        existing.enrollmentFee = request.enrollmentFee
                        existing.installments = installmentDocs
                        existing.currency = currency
                        defaultsRepository.save(existing)
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            defaultsRepository.save(
                                TuitionScheduleDefaults(
                                    schoolId = schoolId,
                                    academicYearId = request.academicYearId,
                                    enrollmentFee = request.enrollmentFee,
                                    installments = installmentDocs,
                                    currency = currency
                                )
                            )
                        }
                    )
            )
            .flatMap { saved ->
                if (seedIds.isEmpty()) {
                    return@flatMap Mono.just(toDefaultsResponse(saved))
                }
                Flux.fromIterable(seedIds)
                    .concatMap { levelId ->
                        scheduleRepository
                            .findBySchoolIdAndAcademicYearIdAndLevelId(
                                schoolId,
                                request.academicYearId,
                                levelId
                            )
                            .map { java.util.Optional.of(it) }
                            .defaultIfEmpty(java.util.Optional.empty())
                            .flatMap { existing ->
                                if (existing.isPresent) {
                                    Mono.empty() // déjà personnalisé → pas d’écrasement auto
                                } else {
                                    levelApi.getSummary(schoolId, levelId)
                                        .flatMap { level ->
                                            val levelLabel = frozenLevelLabel(level.name, level.code)
                                            scheduleRepository.save(
                                                TuitionSchedule(
                                                    schoolId = schoolId,
                                                    academicYearId = request.academicYearId,
                                                    levelId = levelId,
                                                    levelLabel = levelLabel,
                                                    enrollmentFee = saved.enrollmentFee,
                                                    installments = saved.installments
                                                        .map {
                                                            TuitionInstallment(
                                                                index = it.index,
                                                                amount = it.amount,
                                                                dueDate = it.dueDate
                                                            )
                                                        }
                                                        .toMutableList(),
                                                    extraFees = mutableListOf(),
                                                    currency = saved.currency,
                                                    active = true
                                                )
                                            ).map { levelId }
                                        }
                                }
                            }
                    }
                    .collectList()
                    .map { seeded -> toDefaultsResponse(saved, seeded) }
            }
    }

    /**
     * BR-F16 : recopie inscription + tranches du modèle général sur un niveau
     * déjà configuré (conserve les frais d’examen `extraFees`).
     */
    fun syncLevelScheduleFromDefaults(
        currentUser: CurrentUser,
        request: SyncLevelScheduleFromDefaultsRequest
    ): Mono<TuitionScheduleResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        return assertCanManageSchedules(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .then(
                defaultsRepository.findBySchoolIdAndAcademicYearId(schoolId, request.academicYearId)
                    .switchIfEmpty(
                        Mono.error(
                            NotFoundException(
                                "SCHEDULE_DEFAULTS_NOT_FOUND",
                                "Save general defaults before syncing a level"
                            )
                        )
                    )
            )
            .flatMap { defaults ->
                levelApi.getSummary(schoolId, request.levelId).flatMap { level ->
                    val levelLabel = frozenLevelLabel(level.name, level.code)
                    val installmentCopy = defaults.installments
                        .map {
                            TuitionInstallment(
                                index = it.index,
                                amount = it.amount,
                                dueDate = it.dueDate
                            )
                        }
                        .toMutableList()
                    scheduleRepository
                        .findBySchoolIdAndAcademicYearIdAndLevelId(
                            schoolId,
                            request.academicYearId,
                            request.levelId
                        )
                        .flatMap { existing ->
                            existing.levelLabel = levelLabel
                            existing.enrollmentFee = defaults.enrollmentFee
                            existing.installments = installmentCopy
                            // extraFees conservés (examens propres au niveau)
                            existing.currency = defaults.currency
                            existing.active = true
                            scheduleRepository.save(existing)
                        }
                        .switchIfEmpty(
                            Mono.defer {
                                scheduleRepository.save(
                                    TuitionSchedule(
                                        schoolId = schoolId,
                                        academicYearId = request.academicYearId,
                                        levelId = request.levelId,
                                        levelLabel = levelLabel,
                                        enrollmentFee = defaults.enrollmentFee,
                                        installments = installmentCopy,
                                        extraFees = mutableListOf(),
                                        currency = defaults.currency,
                                        active = true
                                    )
                                )
                            }
                        )
                }
            }
            .map { toScheduleResponse(it) }
    }

    fun upsertSchedule(
        currentUser: CurrentUser,
        request: UpsertTuitionScheduleRequest
    ): Mono<TuitionScheduleResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        validateInstallmentIndexes(request)
        validateExtraFeeIndexes(request)

        return assertCanManageSchedules(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .then(levelApi.getSummary(schoolId, request.levelId))
            .flatMap { level ->
                val levelLabel = frozenLevelLabel(level.name, level.code)
                val installmentDocs = request.installments
                    .sortedBy { it.index }
                    .map { dto ->
                        TuitionInstallment(
                            index = dto.index,
                            amount = dto.amount,
                            dueDate = dto.dueDate
                        )
                    }
                    .toMutableList()
                val extraFeeDocs = request.extraFees
                    .sortedBy { it.index }
                    .map { dto ->
                        TuitionExtraFee(
                            index = dto.index,
                            code = dto.code?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                            label = LocalizedText.of(dto.labelFr.trim(), dto.labelEn.trim()),
                            amount = dto.amount,
                            dueDate = dto.dueDate
                        )
                    }
                    .toMutableList()

                scheduleRepository
                    .findBySchoolIdAndAcademicYearIdAndLevelId(
                        schoolId,
                        request.academicYearId,
                        request.levelId
                    )
                    .flatMap { existing ->
                        existing.levelLabel = levelLabel
                        existing.enrollmentFee = request.enrollmentFee
                        existing.installments = installmentDocs
                        existing.extraFees = extraFeeDocs
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
                                    levelId = request.levelId,
                                    levelLabel = levelLabel,
                                    enrollmentFee = request.enrollmentFee,
                                    installments = installmentDocs,
                                    extraFees = extraFeeDocs,
                                    currency = request.currency?.trim()?.ifBlank { "XAF" } ?: "XAF",
                                    active = true
                                )
                            )
                        }
                    )
            }
            .map { toScheduleResponse(it) }
    }

    fun recordPayment(
        currentUser: CurrentUser,
        request: RecordFeePaymentRequest
    ): Mono<FeePaymentResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        if (
            (request.kind == FeePaymentKind.INSTALLMENT || request.kind == FeePaymentKind.EXTRA) &&
            request.installmentIndex == null
        ) {
            return Mono.error(
                BusinessRuleException(
                    "INSTALLMENT_INDEX_REQUIRED",
                    "installmentIndex is required for ${request.kind}"
                )
            )
        }
        if (
            request.kind != FeePaymentKind.INSTALLMENT &&
            request.kind != FeePaymentKind.EXTRA &&
            request.installmentIndex != null
        ) {
            return Mono.error(
                BusinessRuleException(
                    "INSTALLMENT_INDEX_FORBIDDEN",
                    "installmentIndex must be null for ${request.kind}"
                )
            )
        }

        return assertCanRecordPayments(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .then(
                enrollmentApi.findByStudentAndYear(schoolId, request.studentId, request.academicYearId)
            )
            .flatMap { enrollment ->
                findScheduleForClass(schoolId, request.academicYearId, enrollment.classId)
                    .switchIfEmpty(
                        Mono.error(
                            NotFoundException(
                                "TUITION_SCHEDULE_NOT_FOUND",
                                "No tuition schedule for this student's level"
                            )
                        )
                    )
                    .flatMap { (schedule, _) ->
                        Mono.zip(
                            paymentRepository
                                .findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                                    schoolId,
                                    request.academicYearId,
                                    request.studentId,
                                    FeePaymentStatus.RECORDED
                                )
                                .collectList(),
                            stateRepository
                                .findBySchoolIdAndAcademicYearIdAndStudentId(
                                    schoolId,
                                    request.academicYearId,
                                    request.studentId
                                )
                                .map { java.util.Optional.of(it) }
                                .defaultIfEmpty(java.util.Optional.empty())
                        ).flatMap { tuple ->
                            val existingPayments = tuple.t1
                            val state = tuple.t2.orElse(null)
                            validatePaymentRules(schedule, request, existingPayments, state)
                                .then(
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
                                )
                        }
                    }
            }
            .map { toPaymentResponse(it) }
    }

    /**
     * Q-80 / BR-F14 : Direction seule — réactive après soldes tuition + pénalités.
     */
    fun reactivateAfterFees(
        currentUser: CurrentUser,
        studentId: String,
        request: ReactivateAfterFeesRequest
    ): Mono<ReactivateAfterFeesResponse> {
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        if (!currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Mono.error(
                ForbiddenException(
                    "FEES_REACTIVATION_FORBIDDEN",
                    "Only DIRECTOR can reactivate a student after fees exclusion (Q-80)"
                )
            )
        }
        return buildAccount(schoolId, studentId, request.academicYearId)
            .flatMap { account ->
                if (!account.feesExcluded) {
                    return@flatMap Mono.error(
                        BusinessRuleException(
                            "NOT_FEES_EXCLUDED",
                            "Student is not currently excluded for unpaid fees"
                        )
                    )
                }
                if (!account.tuitionSettled) {
                    return@flatMap Mono.error(
                        BusinessRuleException(
                            "TUITION_NOT_SETTLED",
                            "Enrollment, installments and extra fees must be settled before reactivation"
                        )
                    )
                }
                if (!account.penaltySettled) {
                    return@flatMap Mono.error(
                        BusinessRuleException(
                            "PENALTIES_NOT_SETTLED",
                            "Penalties must be settled before reactivation"
                        )
                    )
                }
                studentApi.reactivateAfterFeeExclusion(schoolId, studentId)
                    .flatMap { summary ->
                        stateRepository
                            .findBySchoolIdAndAcademicYearIdAndStudentId(
                                schoolId,
                                request.academicYearId,
                                studentId
                            )
                            .flatMap { state ->
                                state.excludedAt = null
                                state.exclusionReasonCode = null
                                stateRepository.save(state)
                            }
                            .thenReturn(
                                ReactivateAfterFeesResponse(
                                    studentId = summary.id,
                                    academicYearId = request.academicYearId,
                                    active = summary.active,
                                    feesExcluded = false
                                )
                            )
                    }
            }
    }

    /**
     * Reçu PDF inscription (Q-69) — Direction / comptable.
     * Uniquement pour un paiement `ENROLLMENT` enregistré.
     */
    fun enrollmentReceiptPdf(
        currentUser: CurrentUser,
        paymentId: String,
        schoolIdOverride: String? = null
    ): Mono<ByteArray> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanRecordPayments(currentUser, schoolId)
            .then(paymentRepository.findByIdAndSchoolId(paymentId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("FEE_PAYMENT_NOT_FOUND", "Payment not found")))
            .flatMap { payment ->
                if (payment.kind != FeePaymentKind.ENROLLMENT) {
                    return@flatMap Mono.error(
                        BusinessRuleException(
                            "RECEIPT_ENROLLMENT_ONLY",
                            "PDF receipt is only available for enrollment payments"
                        )
                    )
                }
                if (payment.status != FeePaymentStatus.RECORDED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("PAYMENT_VOID", "Cannot print receipt for a void payment")
                    )
                }
                Mono.zip(
                    studentApi.getSummary(schoolId, payment.studentId),
                    schoolClassApi.getSummary(schoolId, payment.classId)
                        .map { it.name }
                        .onErrorReturn("—")
                        .defaultIfEmpty("—"),
                    academicYearApi.getSummary(schoolId, payment.academicYearId)
                        .map { it.label }
                        .onErrorReturn(payment.academicYearId)
                        .defaultIfEmpty(payment.academicYearId),
                    schoolBrandApi.getBulletinBrand(schoolId)
                        .onErrorReturn(emptyBrand(schoolId))
                        .defaultIfEmpty(emptyBrand(schoolId)),
                    scheduleRepository.findByIdAndSchoolId(payment.scheduleId, schoolId)
                        .map { it.currency }
                        .defaultIfEmpty("XAF")
                ).map { tuple ->
                    val student = tuple.t1
                    enrollmentReceiptPdfService.render(
                        brand = tuple.t4,
                        studentName = student.displayName,
                        matricule = student.matricule,
                        classLabel = tuple.t2,
                        academicYearLabel = tuple.t3,
                        amount = payment.amount,
                        currency = tuple.t5,
                        paidAt = payment.paidAt,
                        paymentReference = payment.reference,
                        receiptId = payment.id!!
                    )
                }
            }
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
            .then(assertCanViewStudentFees(currentUser, schoolId, studentId))
            .then(buildAccount(schoolId, studentId, academicYearId))
    }

    /** BR-F05 / Q-65 : état de porte pour soft-block UI (staff, parent lié, élève soi-même). */
    fun getAcademicAccess(
        currentUser: CurrentUser,
        studentId: String,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Mono<FeesAccessGateDto> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanQueryAcademicAccess(currentUser, schoolId, studentId)
            .then(accessGate(schoolId, academicYearId, studentId))
    }

    fun solvencyForStudents(
        currentUser: CurrentUser,
        academicYearId: String,
        studentIds: List<String>,
        schoolIdOverride: String? = null
    ): Flux<SolvencySnapshotDto> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return assertCanReadFees(currentUser, schoolId)
            .then(filterStudentIdsForViewer(currentUser, schoolId, studentIds))
            .flatMapMany { allowedIds -> solvencySnapshot(schoolId, academicYearId, allowedIds) }
    }

    /**
     * Compteurs hub : impayés inscription / tranches + total insolvables (staff uniquement).
     */
    fun unpaidSummary(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Mono<FeesUnpaidSummaryResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        if (!currentUser.hasAnyRole(
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.SURVEILLANT_GENERAL
            )
        ) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Fees unpaid summary requires staff access"))
        }
        val today = LocalDate.now(zone)
        return assertCanReadFees(currentUser, schoolId)
            .thenMany(enrollmentApi.findEnrolledByYear(schoolId, academicYearId))
            .map { it.studentId }
            .distinct()
            .flatMap({ studentId -> computeSolvency(schoolId, studentId, academicYearId, today) }, 16)
            .collectList()
            .map { accounts ->
                var enrollmentUnpaid = 0
                var insolvent = 0
                val unpaidByIndex = sortedMapOf<Int, Int>()
                for (account in accounts) {
                    if (account.status == SolvencyStatus.NOT_APPLICABLE) continue
                    if (account.status == SolvencyStatus.INSOLVENT) insolvent++
                    val due = account.enrollmentFeeDue
                    val paid = account.enrollmentFeePaid
                    if (due != null && paid != null && !SolvencyCalculator.isSettled(due, paid)) {
                        enrollmentUnpaid++
                    }
                    for (inst in account.installments) {
                        if (inst.settled) continue
                        if (inst.dueDate.isAfter(today)) continue
                        unpaidByIndex[inst.index] = (unpaidByIndex[inst.index] ?: 0) + 1
                    }
                }
                FeesUnpaidSummaryResponse(
                    academicYearId = academicYearId,
                    enrollmentUnpaid = enrollmentUnpaid,
                    installmentsUnpaid = unpaidByIndex.map { (index, count) ->
                        InstallmentUnpaidCountDto(index = index, unpaidCount = count)
                    },
                    insolventStudents = insolvent
                )
            }
    }

    /**
     * Tableau de bord Solvabilité : montants, pénalités, grâce, niveaux,
     * encaissements 7/30 j., ponctualité, série quotidienne 30 j.
     */
    fun dashboardSummary(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Mono<FeesDashboardSummaryResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        if (!currentUser.hasAnyRole(
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.SURVEILLANT_GENERAL
            )
        ) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Fees dashboard requires staff access"))
        }
        val today = LocalDate.now(zone)
        val start7 = today.minusDays(6)
        val start30 = today.minusDays(29)

        return assertCanReadFees(currentUser, schoolId)
            .then(
                Mono.zip(
                    enrollmentApi.findEnrolledByYear(schoolId, academicYearId)
                        .map { it.studentId }
                        .distinct()
                        .flatMap({ studentId -> computeSolvency(schoolId, studentId, academicYearId, today) }, 16)
                        .collectList(),
                    paymentRepository
                        .findBySchoolIdAndAcademicYearIdAndStatus(
                            schoolId,
                            academicYearId,
                            FeePaymentStatus.RECORDED
                        )
                        .collectList(),
                    stateRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId).collectList(),
                    policyRepository.findBySchoolId(schoolId)
                        .defaultIfEmpty(
                            com.kobe.beesm.model.fees.FeesPolicy(schoolId = schoolId)
                        )
                )
            )
            .map { tuple ->
                val accounts = tuple.t1
                val payments = tuple.t2
                val states = tuple.t3
                val policy = tuple.t4
                val graceDays = policy.maxInsolvencyGraceDays.coerceAtLeast(1)

                var enrollmentUnpaid = 0
                var insolvent = 0
                var solvent = 0
                var pending = 0
                var notApplicable = 0
                var totalDue = BigDecimal.ZERO
                var totalCollected = BigDecimal.ZERO
                var onTime = 0
                var late = 0
                var unpaidDue = 0
                val unpaidByIndex = sortedMapOf<Int, Int>()
                val insolventByLevel = linkedMapOf<String, Pair<String, IntArray>>()
                var currency = policy.currency.ifBlank { "XAF" }

                for (account in accounts) {
                    when (account.status) {
                        SolvencyStatus.NOT_APPLICABLE -> {
                            notApplicable++
                            continue
                        }
                        SolvencyStatus.INSOLVENT -> insolvent++
                        SolvencyStatus.PENDING -> pending++
                        SolvencyStatus.SOLVENT -> solvent++
                    }
                    if (!account.currency.isNullOrBlank()) {
                        currency = account.currency
                    }
                    val levelKey = account.levelId ?: "_unknown"
                    val levelLabel = account.classLabel ?: levelKey
                    val bucket = insolventByLevel.getOrPut(levelKey) {
                        levelLabel to intArrayOf(0, 0)
                    }
                    bucket.second[1]++
                    if (account.status == SolvencyStatus.INSOLVENT) {
                        bucket.second[0]++
                    }

                    val dueEnroll = account.enrollmentFeeDue ?: BigDecimal.ZERO
                    val paidEnroll = account.enrollmentFeePaid ?: BigDecimal.ZERO
                    totalDue = totalDue.add(dueEnroll)
                    totalCollected = totalCollected.add(paidEnroll)
                    if (dueEnroll.compareTo(BigDecimal.ZERO) > 0 &&
                        !SolvencyCalculator.isSettled(dueEnroll, paidEnroll)
                    ) {
                        enrollmentUnpaid++
                    }

                    for (inst in account.installments) {
                        totalDue = totalDue.add(inst.amount)
                        totalCollected = totalCollected.add(inst.paidAmount)
                        if (!inst.settled && !inst.dueDate.isAfter(today)) {
                            unpaidByIndex[inst.index] = (unpaidByIndex[inst.index] ?: 0) + 1
                        }
                    }
                    for (extra in account.extraFees) {
                        totalDue = totalDue.add(extra.amount)
                        totalCollected = totalCollected.add(extra.paidAmount)
                    }
                }

                for (account in accounts) {
                    if (account.status == SolvencyStatus.NOT_APPLICABLE) continue
                    for (inst in account.installments) {
                        if (!inst.settled) {
                            if (!inst.dueDate.isAfter(today)) unpaidDue++
                            continue
                        }
                        val earliest = payments
                            .asSequence()
                            .filter {
                                it.studentId == account.studentId &&
                                    it.kind == FeePaymentKind.INSTALLMENT &&
                                    it.installmentIndex == inst.index
                            }
                            .map { it.paidAt }
                            .minOrNull()
                        if (earliest == null) {
                            onTime++
                            continue
                        }
                        val paidDay = earliest.atZone(zone).toLocalDate()
                        if (!paidDay.isAfter(inst.dueDate)) onTime++ else late++
                    }
                }

                val statesWithPenalty = states.filter {
                    it.penaltyWeeksAccrued > 0 || it.penaltyTotalAccrued.compareTo(BigDecimal.ZERO) > 0
                }
                val penaltyTotal = states.fold(BigDecimal.ZERO) { acc, s ->
                    acc.add(s.penaltyTotalAccrued)
                }

                var band14 = 0
                var band21 = 0
                var bandMax = 0
                for (state in states) {
                    val since = state.insolvencySince ?: continue
                    if (state.excludedAt != null) continue
                    val days = ChronoUnit.DAYS.between(since, today).toInt()
                    when {
                        days >= graceDays -> bandMax++
                        days >= 21 -> band21++
                        days >= 14 -> band14++
                    }
                }

                fun amountInRange(from: LocalDate, to: LocalDate): Pair<Int, BigDecimal> {
                    var count = 0
                    var amount = BigDecimal.ZERO
                    for (p in payments) {
                        val day = p.paidAt.atZone(zone).toLocalDate()
                        if (!day.isBefore(from) && !day.isAfter(to)) {
                            count++
                            amount = amount.add(p.amount)
                        }
                    }
                    return count to amount
                }
                val (c7, a7) = amountInRange(start7, today)
                val (c30, a30) = amountInRange(start30, today)

                val dailyMap = (0L..29L).associate { offset ->
                    start30.plusDays(offset) to (0 to BigDecimal.ZERO)
                }.toMutableMap()
                for (p in payments) {
                    val day = p.paidAt.atZone(zone).toLocalDate()
                    if (day.isBefore(start30) || day.isAfter(today)) continue
                    val prev = dailyMap[day] ?: (0 to BigDecimal.ZERO)
                    dailyMap[day] = (prev.first + 1) to prev.second.add(p.amount)
                }
                val daily = dailyMap.entries
                    .sortedBy { it.key }
                    .map { (date, pair) ->
                        DailyAmountDto(date = date, amount = pair.second, count = pair.first)
                    }

                val outstanding = totalDue.subtract(totalCollected).max(BigDecimal.ZERO)

                FeesDashboardSummaryResponse(
                    academicYearId = academicYearId,
                    currency = currency,
                    enrollmentUnpaid = enrollmentUnpaid,
                    installmentsUnpaid = unpaidByIndex.map { (index, count) ->
                        InstallmentUnpaidCountDto(index = index, unpaidCount = count)
                    },
                    insolventStudents = insolvent,
                    solventStudents = solvent,
                    pendingStudents = pending,
                    notApplicableStudents = notApplicable,
                    totalDue = totalDue,
                    totalCollected = totalCollected,
                    totalOutstanding = outstanding,
                    enrollmentFeeUnpaidStudents = enrollmentUnpaid,
                    studentsWithPenalties = statesWithPenalty.size,
                    penaltyTotalAmount = penaltyTotal,
                    exclusionRisk = ExclusionRiskDto(
                        day14 = band14,
                        day21 = band21,
                        day28OrMax = bandMax,
                        graceDays = graceDays
                    ),
                    insolventByLevel = insolventByLevel.entries
                        .map { (levelId, pair) ->
                            LevelInsolvencyDto(
                                levelId = levelId,
                                levelLabel = pair.first,
                                insolventCount = pair.second[0],
                                studentCount = pair.second[1]
                            )
                        }
                        .sortedByDescending { it.insolventCount }
                        .take(12),
                    recentPayments = RecentPaymentsDto(
                        last7DaysCount = c7,
                        last7DaysAmount = a7,
                        last30DaysCount = c30,
                        last30DaysAmount = a30
                    ),
                    punctuality = PunctualityStatsDto(
                        onTimeCount = onTime,
                        lateCount = late,
                        unpaidDueCount = unpaidDue
                    ),
                    dailyCollections = daily
                )
            }
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

    override fun accessGate(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<FeesAccessGateDto> {
        val today = LocalDate.now(zone)
        return computeSolvency(schoolId, studentId, academicYearId, today)
            .map { solvency ->
                val enrollmentSettled =
                    solvency.enrollmentFeeDue == null ||
                        SolvencyCalculator.isSettled(
                            solvency.enrollmentFeeDue,
                            solvency.enrollmentFeePaid ?: BigDecimal.ZERO
                        )
                val blocked =
                    solvency.status != SolvencyStatus.NOT_APPLICABLE &&
                        (!enrollmentSettled || solvency.status == SolvencyStatus.INSOLVENT)
                FeesAccessGateDto(
                    studentId = studentId,
                    academicYearId = academicYearId,
                    solvency = solvency.status,
                    blocked = blocked,
                    enrollmentSettled = enrollmentSettled,
                    reasonCode = if (blocked) "FEES_UNPAID" else null,
                    messageFr = if (blocked) {
                        "Consultation ou modification impossible : frais de scolarité non soldés."
                    } else {
                        null
                    },
                    messageEn = if (blocked) {
                        "Access denied: tuition fees are not settled."
                    } else {
                        null
                    }
                )
            }
    }

    override fun assertCanModifyAcademics(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<Void> =
        accessGate(schoolId, academicYearId, studentId).flatMap { gate ->
            if (!gate.blocked) {
                Mono.empty()
            } else {
                Mono.error(
                    ForbiddenException(
                        gate.reasonCode ?: "FEES_UNPAID",
                        gate.messageEn ?: "Access denied: tuition fees are not settled."
                    )
                )
            }
        }

    override fun assertCanViewAcademics(
        currentUser: CurrentUser,
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<Void> {
        if (currentUser.hasAnyRole(
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.ADMINISTRATION,
                Role.SURVEILLANT_GENERAL
            )
        ) {
            return Mono.empty()
        }
        return assertCanModifyAcademics(schoolId, academicYearId, studentId)
    }

    private fun buildAccount(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<StudentFeeAccountResponse> {
        val today = LocalDate.now(zone)
        return computeSolvency(schoolId, studentId, academicYearId, today)
            .flatMap { solvency ->
                Mono.zip(
                    paymentRepository
                        .findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                            schoolId,
                            academicYearId,
                            studentId,
                            FeePaymentStatus.RECORDED
                        )
                        .collectList(),
                    paymentRepository
                        .findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                            schoolId,
                            academicYearId,
                            studentId,
                            FeePaymentStatus.VOID
                        )
                        .collectList(),
                    stateRepository
                        .findBySchoolIdAndAcademicYearIdAndStudentId(
                            schoolId,
                            academicYearId,
                            studentId
                        )
                        .map { java.util.Optional.of(it) }
                        .defaultIfEmpty(java.util.Optional.empty()),
                    studentApi.getSummary(schoolId, studentId)
                ).map { tuple ->
                    val payments = tuple.t1
                    val voidedPayments = tuple.t2
                    val state = tuple.t3.orElse(null)
                    val student = tuple.t4
                    val penaltyAccrued = state?.penaltyTotalAccrued ?: BigDecimal.ZERO
                    val penaltyPaid = sumPayments(payments, FeePaymentKind.PENALTY, null)
                    val penaltySettled =
                        penaltyAccrued.compareTo(BigDecimal.ZERO) <= 0 ||
                            SolvencyCalculator.isSettled(penaltyAccrued, penaltyPaid)
                    val enrollmentSettled =
                        solvency.enrollmentFeeDue == null ||
                            SolvencyCalculator.isSettled(
                                solvency.enrollmentFeeDue,
                                solvency.enrollmentFeePaid ?: BigDecimal.ZERO
                            )
                    val installmentsSettled =
                        solvency.installments.isEmpty() ||
                            solvency.installments.all { it.settled }
                    val extraFeesSettled =
                        solvency.extraFees.isEmpty() ||
                            solvency.extraFees.all { it.settled }
                    val tuitionSettled = enrollmentSettled && installmentsSettled && extraFeesSettled
                    val feesExcluded =
                        state?.excludedAt != null &&
                            state.exclusionReasonCode == "FEES_INSOLVENCY_EXCLUSION" &&
                            !student.active
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
                        extraFees = solvency.extraFees,
                        payments = payments.map { toPaymentResponse(it) },
                        voidedPayments = voidedPayments.map { toPaymentResponse(it) },
                        feesExcluded = feesExcluded,
                        penaltyTotalAccrued = penaltyAccrued,
                        penaltyPaid = penaltyPaid,
                        penaltySettled = penaltySettled,
                        tuitionSettled = tuitionSettled,
                        canReactivateAfterFees = feesExcluded && tuitionSettled && penaltySettled
                    )
                }
            }
    }

    private data class SolvencyInternal(
        val studentId: String,
        val status: SolvencyStatus,
        val enrollmentId: String?,
        val classId: String?,
        val levelId: String?,
        val classLabel: String?,
        val scheduleId: String?,
        val currency: String?,
        val enrollmentFeeDue: BigDecimal?,
        val enrollmentFeePaid: BigDecimal?,
        val installments: List<InstallmentSolvencyDto>,
        val extraFees: List<ExtraFeeSolvencyDto> = emptyList()
    )

    /** Enrollment.classId → SchoolClass.levelId → TuitionSchedule (Q-67). */
    private fun findScheduleForClass(
        schoolId: String,
        academicYearId: String,
        classId: String
    ): Mono<Pair<TuitionSchedule, String>> =
        schoolClassApi.getSummary(schoolId, classId)
            .flatMap { clazz ->
                scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(
                    schoolId,
                    academicYearId,
                    clazz.levelId
                ).map { schedule -> schedule to clazz.levelId }
            }
            .onErrorResume(NotFoundException::class.java) { Mono.empty() }

    private fun frozenLevelLabel(name: LocalizedTextDto, code: String): String =
        name.fr?.trim()?.takeIf { it.isNotEmpty() }
            ?: name.en?.trim()?.takeIf { it.isNotEmpty() }
            ?: code

    private fun computeSolvency(
        schoolId: String,
        studentId: String,
        academicYearId: String,
        today: LocalDate
    ): Mono<SolvencyInternal> =
        enrollmentApi.findByStudentAndYear(schoolId, studentId, academicYearId)
            .onErrorResume(NotFoundException::class.java) { Mono.empty() }
            .flatMap { enrollment ->
                schoolClassApi.getSummary(schoolId, enrollment.classId)
                    .onErrorResume(NotFoundException::class.java) { Mono.empty() }
                    .flatMap { clazz ->
                        scheduleRepository
                            .findBySchoolIdAndAcademicYearIdAndLevelId(
                                schoolId,
                                academicYearId,
                                clazz.levelId
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
                                        val extraFeeDtos = schedule.extraFees
                                            .sortedBy { it.index }
                                            .map { extra ->
                                                val paid = sumPayments(
                                                    payments,
                                                    FeePaymentKind.EXTRA,
                                                    extra.index
                                                )
                                                ExtraFeeSolvencyDto(
                                                    index = extra.index,
                                                    code = extra.code,
                                                    labelFr = extra.label.fr?.trim().orEmpty(),
                                                    labelEn = extra.label.en?.trim().orEmpty(),
                                                    amount = extra.amount,
                                                    dueDate = extra.dueDate,
                                                    paidAmount = paid,
                                                    settled = SolvencyCalculator.isSettled(extra.amount, paid)
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
                                            },
                                            extraFees = extraFeeDtos.map {
                                                SolvencyCalculator.InstallmentInput(
                                                    index = it.index,
                                                    amount = it.amount,
                                                    dueDate = it.dueDate,
                                                    paidAmount = it.paidAmount
                                                )
                                            }
                                        )
                                        SolvencyInternal(
                                            studentId = studentId,
                                            status = status,
                                            enrollmentId = enrollment.id,
                                            classId = enrollment.classId,
                                            levelId = clazz.levelId,
                                            classLabel = schedule.levelLabel,
                                            scheduleId = schedule.id,
                                            currency = schedule.currency,
                                            enrollmentFeeDue = schedule.enrollmentFee,
                                            enrollmentFeePaid = enrollmentPaid,
                                            installments = installmentDtos,
                                            extraFees = extraFeeDtos
                                        )
                                    }
                            }
                            .switchIfEmpty(
                                Mono.just(
                                    SolvencyInternal(
                                        studentId = studentId,
                                        status = SolvencyStatus.NOT_APPLICABLE,
                                        enrollmentId = enrollment.id,
                                        classId = enrollment.classId,
                                        levelId = clazz.levelId,
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
                            studentId = studentId,
                            status = SolvencyStatus.NOT_APPLICABLE,
                            enrollmentId = enrollment.id,
                            classId = enrollment.classId,
                            levelId = null,
                            classLabel = null,
                            scheduleId = null,
                            currency = null,
                            enrollmentFeeDue = null,
                            enrollmentFeePaid = null,
                            installments = emptyList()
                        )
                    )
            }
            .defaultIfEmpty(
                SolvencyInternal(
                    studentId = studentId,
                    status = SolvencyStatus.NOT_APPLICABLE,
                    enrollmentId = null,
                    classId = null,
                    levelId = null,
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
        validateInstallmentList(request.installments)
    }

    private fun validateDefaultsInstallments(request: UpsertTuitionScheduleDefaultsRequest) {
        validateInstallmentList(request.installments)
    }

    private fun validateInstallmentList(
        installments: List<com.kobe.beesm.dto.fees.TuitionInstallmentAmountDto>
    ) {
        val n = installments.size
        if (n !in 1..12) {
            throw BusinessRuleException(
                "INVALID_INSTALLMENTS",
                "Between 1 and 12 installments are required"
            )
        }
        val indexes = installments.map { it.index }.sorted()
        val expected = (1..n).toList()
        if (indexes != expected) {
            throw BusinessRuleException(
                "INVALID_INSTALLMENTS",
                "Installment indexes must be contiguous from 1 to $n"
            )
        }
    }

    private fun toDefaultsResponse(
        defaults: TuitionScheduleDefaults,
        seededLevelIds: List<String> = emptyList()
    ): TuitionScheduleDefaultsResponse {
        val id = defaults.id?.trim().orEmpty()
        require(id.isNotEmpty()) { "Tuition schedule defaults id missing" }
        return TuitionScheduleDefaultsResponse(
            id = id,
            schoolId = defaults.schoolId,
            academicYearId = defaults.academicYearId,
            enrollmentFee = defaults.enrollmentFee,
            installments = defaults.installments
                .sortedBy { it.index }
                .map {
                    TuitionInstallmentResponse(
                        index = it.index,
                        amount = it.amount,
                        dueDate = it.dueDate
                    )
                },
            currency = defaults.currency,
            seededLevelIds = seededLevelIds
        )
    }

    private fun validateExtraFeeIndexes(request: UpsertTuitionScheduleRequest) {
        val extras = request.extraFees
        if (extras.isEmpty()) return
        if (extras.size > 6) {
            throw BusinessRuleException(
                "INVALID_EXTRA_FEES",
                "At most 6 extra fees are allowed"
            )
        }
        val indexes = extras.map { it.index }.sorted()
        val expected = (1..extras.size).toList()
        if (indexes != expected) {
            throw BusinessRuleException(
                "INVALID_EXTRA_FEES",
                "Extra fee indexes must be contiguous from 1 to ${extras.size}"
            )
        }
        for (extra in extras) {
            val fr = extra.labelFr.trim()
            val en = extra.labelEn.trim()
            if (fr.isEmpty() || en.isEmpty()) {
                throw BusinessRuleException(
                    "INVALID_EXTRA_FEES",
                    "Extra fee labels must be provided in French and English"
                )
            }
        }
    }

    private fun resolveAccess(currentUser: CurrentUser, schoolId: String): Mono<FeesAccessResponse> {
        // Enseignant : pas de solvabilité. Parent : lecture limitée à ses enfants (contrôle fin).
        val canRead = currentUser.hasAnyRole(
            Role.ADMINISTRATION,
            Role.DIRECTOR,
            Role.SUPER_ADMIN,
            Role.SURVEILLANT_GENERAL,
            Role.PARENT
        )
        val canManageSchedules = currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)
        if (canManageSchedules) {
            return Mono.just(
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = true,
                    canRecordPayments = true,
                    canManagePolicy = true
                )
            )
        }
        if (!currentUser.hasRole(Role.ADMINISTRATION)) {
            return Mono.just(
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = false,
                    canRecordPayments = false,
                    canManagePolicy = false
                )
            )
        }
        return isAccountantStaff(currentUser, schoolId)
            .map { accountant ->
                FeesAccessResponse(
                    canRead = canRead,
                    canManageSchedules = false,
                    canRecordPayments = accountant,
                    canManagePolicy = false
                )
            }
    }

    /** Parent : uniquement ses enfants liés ; staff autorisé : tous. */
    private fun assertCanViewStudentFees(
        currentUser: CurrentUser,
        schoolId: String,
        studentId: String
    ): Mono<Void> {
        if (!isParentViewer(currentUser)) {
            return Mono.empty()
        }
        val guardianId = currentUser.profileId?.trim().orEmpty()
        if (guardianId.isEmpty()) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Parent profile required"))
        }
        return studentApi.findGuardianLink(schoolId, studentId, guardianId)
            .switchIfEmpty(
                Mono.error(ForbiddenException("FORBIDDEN", "Fees visible only for linked children"))
            )
            .then()
    }

    private fun assertCanQueryAcademicAccess(
        currentUser: CurrentUser,
        schoolId: String,
        studentId: String
    ): Mono<Void> {
        if (currentUser.hasRole(Role.STUDENT) &&
            currentUser.profileType == ProfileType.STUDENT &&
            currentUser.profileId == studentId
        ) {
            return Mono.empty()
        }
        if (isParentViewer(currentUser)) {
            return assertCanViewStudentFees(currentUser, schoolId, studentId)
        }
        return assertCanReadFees(currentUser, schoolId)
    }

    private fun filterStudentIdsForViewer(
        currentUser: CurrentUser,
        schoolId: String,
        studentIds: List<String>
    ): Mono<List<String>> {
        val requested = studentIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (requested.isEmpty()) {
            return Mono.just(emptyList())
        }
        if (!isParentViewer(currentUser)) {
            return Mono.just(requested)
        }
        val guardianId = currentUser.profileId?.trim().orEmpty()
        if (guardianId.isEmpty()) {
            return Mono.just(emptyList())
        }
        return studentApi.findByGuardian(schoolId, guardianId)
            .map { it.id }
            .collectList()
            .map { linked ->
                val allowed = linked.toSet()
                requested.filter { it in allowed }
            }
    }

    private fun isParentViewer(currentUser: CurrentUser): Boolean =
        currentUser.hasRole(Role.PARENT) &&
            currentUser.profileType == ProfileType.GUARDIAN &&
            !currentUser.profileId.isNullOrBlank() &&
            !currentUser.hasAnyRole(
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.SURVEILLANT_GENERAL
            )

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

    /**
     * Q-68/Q-74/Q-75/Q-80/Q-81 : CASH inscription ; montant exact ;
     * ordre inscription → T1 → T2… → extras → pénalités.
     */
    private fun validatePaymentRules(
        schedule: TuitionSchedule,
        request: RecordFeePaymentRequest,
        existingPayments: List<FeePayment>,
        state: FeeStudentFeeState?
    ): Mono<Void> {
        if (request.kind == FeePaymentKind.PENALTY) {
            val accrued = state?.penaltyTotalAccrued ?: BigDecimal.ZERO
            val paid = sumPayments(existingPayments, FeePaymentKind.PENALTY, null)
            val remaining = accrued.subtract(paid)
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.error(
                    BusinessRuleException("PENALTIES_ALREADY_SETTLED", "Penalties are already settled")
                )
            }
            if (request.amount.compareTo(remaining) != 0) {
                return Mono.error(
                    BusinessRuleException(
                        "AMOUNT_MUST_MATCH_DUE",
                        "Payment amount must equal the exact remaining penalty due ($remaining)"
                    )
                )
            }
            return Mono.empty()
        }

        if (request.kind == FeePaymentKind.ENROLLMENT) {
            if (request.method != FeePaymentMethod.CASH) {
                return Mono.error(
                    BusinessRuleException(
                        "ENROLLMENT_CASH_ONLY",
                        "Enrollment fees must be paid in cash at the school"
                    )
                )
            }
            val due = schedule.enrollmentFee
            val paid = sumPayments(existingPayments, FeePaymentKind.ENROLLMENT, null)
            val remaining = due.subtract(paid)
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.error(
                    BusinessRuleException("ENROLLMENT_ALREADY_SETTLED", "Enrollment fee is already settled")
                )
            }
            if (request.amount.compareTo(remaining) != 0) {
                return Mono.error(
                    BusinessRuleException(
                        "AMOUNT_MUST_MATCH_DUE",
                        "Payment amount must equal the exact remaining due ($remaining)"
                    )
                )
            }
            return Mono.empty()
        }

        if (request.kind == FeePaymentKind.EXTRA) {
            return validateExtraPayment(schedule, request, existingPayments)
        }

        val idx = request.installmentIndex
            ?: return Mono.error(
                BusinessRuleException("INSTALLMENT_INDEX_REQUIRED", "installmentIndex is required for INSTALLMENT")
            )
        val installment = schedule.installments.firstOrNull { it.index == idx }
            ?: return Mono.error(
                BusinessRuleException("INVALID_INSTALLMENT_INDEX", "Installment index $idx not in schedule")
            )

        val enrollmentPaid = sumPayments(existingPayments, FeePaymentKind.ENROLLMENT, null)
        if (!SolvencyCalculator.isSettled(schedule.enrollmentFee, enrollmentPaid)) {
            return Mono.error(
                BusinessRuleException(
                    "ENROLLMENT_REQUIRED_FIRST",
                    "Enrollment fee must be settled before installment payments"
                )
            )
        }

        val priorIndexes = schedule.installments.map { it.index }.filter { it < idx }.sorted()
        for (prior in priorIndexes) {
            val priorDue = schedule.installments.first { it.index == prior }.amount
            val priorPaid = sumPayments(existingPayments, FeePaymentKind.INSTALLMENT, prior)
            if (!SolvencyCalculator.isSettled(priorDue, priorPaid)) {
                return Mono.error(
                    BusinessRuleException(
                        "INSTALLMENTS_MUST_BE_SEQUENTIAL",
                        "Installment $prior must be settled before installment $idx"
                    )
                )
            }
        }

        val paid = sumPayments(existingPayments, FeePaymentKind.INSTALLMENT, idx)
        val remaining = installment.amount.subtract(paid)
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(
                BusinessRuleException("INSTALLMENT_ALREADY_SETTLED", "Installment $idx is already settled")
            )
        }
        if (request.amount.compareTo(remaining) != 0) {
            return Mono.error(
                BusinessRuleException(
                    "AMOUNT_MUST_MATCH_DUE",
                    "Payment amount must equal the exact remaining due ($remaining)"
                )
            )
        }
        return Mono.empty()
    }

    /** Q-81 provisoire : extras après toutes les tranches, ordre séquentiel par index. */
    private fun validateExtraPayment(
        schedule: TuitionSchedule,
        request: RecordFeePaymentRequest,
        existingPayments: List<FeePayment>
    ): Mono<Void> {
        val idx = request.installmentIndex
            ?: return Mono.error(
                BusinessRuleException("INSTALLMENT_INDEX_REQUIRED", "installmentIndex is required for EXTRA")
            )
        val extra = schedule.extraFees.firstOrNull { it.index == idx }
            ?: return Mono.error(
                BusinessRuleException("INVALID_EXTRA_FEE_INDEX", "Extra fee index $idx not in schedule")
            )

        val enrollmentPaid = sumPayments(existingPayments, FeePaymentKind.ENROLLMENT, null)
        if (!SolvencyCalculator.isSettled(schedule.enrollmentFee, enrollmentPaid)) {
            return Mono.error(
                BusinessRuleException(
                    "ENROLLMENT_REQUIRED_FIRST",
                    "Enrollment fee must be settled before extra fees"
                )
            )
        }
        for (inst in schedule.installments.sortedBy { it.index }) {
            val paid = sumPayments(existingPayments, FeePaymentKind.INSTALLMENT, inst.index)
            if (!SolvencyCalculator.isSettled(inst.amount, paid)) {
                return Mono.error(
                    BusinessRuleException(
                        "INSTALLMENTS_REQUIRED_BEFORE_EXTRA",
                        "All installments must be settled before extra fees"
                    )
                )
            }
        }
        val priorExtras = schedule.extraFees.map { it.index }.filter { it < idx }.sorted()
        for (prior in priorExtras) {
            val priorDue = schedule.extraFees.first { it.index == prior }.amount
            val priorPaid = sumPayments(existingPayments, FeePaymentKind.EXTRA, prior)
            if (!SolvencyCalculator.isSettled(priorDue, priorPaid)) {
                return Mono.error(
                    BusinessRuleException(
                        "EXTRA_FEES_MUST_BE_SEQUENTIAL",
                        "Extra fee $prior must be settled before extra fee $idx"
                    )
                )
            }
        }

        val paid = sumPayments(existingPayments, FeePaymentKind.EXTRA, idx)
        val remaining = extra.amount.subtract(paid)
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(
                BusinessRuleException("EXTRA_FEE_ALREADY_SETTLED", "Extra fee $idx is already settled")
            )
        }
        if (request.amount.compareTo(remaining) != 0) {
            return Mono.error(
                BusinessRuleException(
                    "AMOUNT_MUST_MATCH_DUE",
                    "Payment amount must equal the exact remaining due ($remaining)"
                )
            )
        }
        return Mono.empty()
    }

    private fun toScheduleResponse(schedule: TuitionSchedule): TuitionScheduleResponse {
        val id = schedule.id?.trim().orEmpty()
        val levelId = schedule.levelId?.trim().orEmpty()
        require(id.isNotEmpty()) { "Tuition schedule id missing" }
        require(levelId.isNotEmpty()) { "Tuition schedule levelId missing" }
        return TuitionScheduleResponse(
            id = id,
            schoolId = schedule.schoolId,
            academicYearId = schedule.academicYearId,
            levelId = levelId,
            levelLabel = schedule.levelLabel?.trim().orEmpty().ifEmpty { levelId },
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
            extraFees = schedule.extraFees
                .sortedBy { it.index }
                .map {
                    TuitionExtraFeeResponse(
                        index = it.index,
                        code = it.code,
                        labelFr = it.label.fr?.trim().orEmpty(),
                        labelEn = it.label.en?.trim().orEmpty(),
                        amount = it.amount,
                        dueDate = it.dueDate
                    )
                },
            currency = schedule.currency,
            active = schedule.active
        )
    }

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

    private fun emptyBrand(schoolId: String): SchoolBulletinBrandDto =
        SchoolBulletinBrandDto(
            schoolId = schoolId,
            nameFr = "",
            nameEn = "",
            postalBox = null,
            republicFr = null,
            republicEn = null,
            mottoFr = null,
            mottoEn = null,
            ministryFr = null,
            ministryEn = null,
            regionalDelegationFr = null,
            regionalDelegationEn = null,
            divisionalDelegationFr = null,
            divisionalDelegationEn = null
        )

}
