package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.AcademicYearResponse
import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import com.kobe.smels.dto.academic.CreateAcademicYearRequest
import com.kobe.smels.dto.academic.PageResponse
import com.kobe.smels.dto.academic.UpdateAcademicYearRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

@Service
class AcademicYearService(
    private val academicYearRepository: AcademicYearRepository,
    private val schoolRepository: SchoolRepository,
    private val outboxWriter: OutboxWriter
) : AcademicYearApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateAcademicYearRequest): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        AcademicAccess.logSuperAdmin(currentUser, schoolId, "academicYear.create")
        validateTerms(request.terms.map { it.number })
        val label = request.label.trim()
        log.info("academicYear.create start by={} schoolId={} label={}", currentUser.username, schoolId, label)

        return ensureLabelAvailable(schoolId, label)
            .then(
                Mono.fromCallable {
                    AcademicYear(
                        schoolId = schoolId,
                        label = label,
                        startDate = request.startDate,
                        endDate = request.endDate,
                        current = request.current,
                        status = request.status,
                        terms = request.terms.map { AcademicMapper.toTerm(it) }
                    )
                }
            )
            .flatMap { year ->
                val saveMono = if (year.current) clearCurrent(schoolId).then(academicYearRepository.save(year))
                else academicYearRepository.save(year)
                saveMono
            }
            .flatMap { saved ->
                outboxWriter.append(
                    schoolId = saved.schoolId,
                    module = "academic",
                    aggregateType = "AcademicYear",
                    aggregateId = saved.id!!,
                    eventType = "academic.year.created",
                    payload = mapOf(
                        "academicYearId" to saved.id,
                        "label" to saved.label,
                        "current" to saved.current,
                        "status" to saved.status.name
                    )
                ).then(mapYearResponse(saved))
            }
            .doOnNext {
                log.info("academicYear.create ok by={} schoolId={} yearId={}", currentUser.username, schoolId, it.id)
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateAcademicYearRequest,
        schoolIdOverride: String? = null
    ): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        validateTerms(request.terms.map { it.number })
        val label = request.label.trim()
        return academicYearRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { year ->
                assertWritableEntity(schoolId, year)
                    .then(ensureLabelAvailable(schoolId, label, excludeId = id))
                    .then(
                        Mono.defer {
                            year.label = label
                            year.startDate = request.startDate
                            year.endDate = request.endDate
                            // Ne pas réactiver une année déjà CLOSE via update manuel si la date de fin est atteinte.
                            year.status = request.status
                            year.terms = request.terms.map { AcademicMapper.toTerm(it) }
                            val prepare = if (request.current && !year.current) clearCurrent(schoolId) else Mono.empty()
                            year.current = request.current
                            prepare.then(academicYearRepository.save(year))
                        }
                    )
            }
            .flatMap { mapYearResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureYearLifecycle(schoolId)
            .then(academicYearRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { mapYearResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<AcademicYearResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("startDate").descending())
        return ensureYearLifecycle(schoolId)
            .then(schoolToday(schoolId))
            .flatMap { today ->
                academicYearRepository.findBySchoolId(schoolId, pageable)
                    .map { AcademicMapper.toYearResponse(it, today) }
                    .collectList()
                    .zipWith(academicYearRepository.countBySchoolId(schoolId))
            }
            .map { tuple ->
                val total = tuple.t2
                PageResponse(
                    content = tuple.t1,
                    page = pageable.pageNumber,
                    size = pageable.pageSize,
                    totalElements = total,
                    totalPages = if (total == 0L) 0 else ceil(total.toDouble() / pageable.pageSize).toInt()
                )
            }
    }

    /**
     * Définit manuellement l’année courante (consultation d’une année passée ou préparation
     * de la suivante). Une année CLOSE reste CLOSE — le contexte change, pas le verrouillage.
     * Réservé au directeur (+ SUPER_ADMIN).
     */
    fun setCurrent(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanManageSchoolSettings(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureYearLifecycle(schoolId)
            .then(academicYearRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { year ->
                if (year.current) {
                    Mono.just(year)
                } else {
                    clearCurrent(schoolId)
                        .then(
                            Mono.defer {
                                year.current = true
                                // N’activer que si l’année n’est pas déjà verrouillée.
                                schoolToday(schoolId).flatMap { today ->
                                    if (!AcademicMapper.yearIsLocked(year, today) &&
                                        year.status == AcademicYearStatus.PLANNED
                                    ) {
                                        year.status = AcademicYearStatus.ACTIVE
                                    }
                                    academicYearRepository.save(year)
                                }
                            }
                        )
                }
            }
            .flatMap { mapYearResponse(it) }
            .doOnNext {
                log.info(
                    "academicYear.setCurrent ok by={} schoolId={} yearId={} label={} locked={}",
                    currentUser.username,
                    schoolId,
                    it.id,
                    it.label,
                    it.locked
                )
            }
    }

    override fun getSummary(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto> =
        ensureYearLifecycle(schoolId)
            .then(academicYearRepository.findByIdAndSchoolId(academicYearId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { mapYearSummary(it) }

    override fun findCurrent(schoolId: String): Mono<AcademicYearSummaryDto> =
        ensureYearLifecycle(schoolId)
            .then(
                academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
                    .switchIfEmpty(
                        Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "No current academic year"))
                    )
            )
            .flatMap { mapYearSummary(it) }

    override fun assertWritable(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto> =
        ensureYearLifecycle(schoolId)
            .then(academicYearRepository.findByIdAndSchoolId(academicYearId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { year -> assertWritableEntity(schoolId, year).then(mapYearSummary(year)) }

    /**
     * Cycle de vie automatique (timezone école) :
     * 1. Toute année avec `today >= endDate` passe en CLOSED (verrouillage total).
     * 2. Si l’année courante est verrouillée et qu’une année opérationnelle
     *    (`startDate <= today < endDate`) existe, elle devient courante / ACTIVE.
     * 3. Sinon, si aucune courante : choisir l’année opérationnelle, sinon la plus récente.
     */
    fun ensureYearLifecycle(schoolId: String): Mono<Void> =
        schoolToday(schoolId)
            .flatMap { today ->
                academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId)
                    .collectList()
                    .flatMap { years ->
                        if (years.isEmpty()) {
                            Mono.empty()
                        } else {
                            closeExpiredYears(years, today)
                                .then(
                                    Mono.defer {
                                        academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId)
                                            .collectList()
                                            .flatMap { refreshed -> reconcileCurrent(schoolId, refreshed, today) }
                                    }
                                )
                        }
                    }
            }

    /** Conservé pour les appels existants ; délègue au cycle de vie complet. */
    fun ensureCurrentYear(schoolId: String): Mono<Void> = ensureYearLifecycle(schoolId)

    private fun closeExpiredYears(years: List<AcademicYear>, today: LocalDate): Mono<Void> {
        val expired = years.filter { year ->
            !today.isBefore(year.endDate) &&
                year.status != AcademicYearStatus.CLOSED &&
                year.status != AcademicYearStatus.ARCHIVED
        }
        if (expired.isEmpty()) return Mono.empty()
        return Flux.fromIterable(expired)
            .concatMap { year ->
                year.status = AcademicYearStatus.CLOSED
                log.info(
                    "academicYear.autoClose schoolId={} yearId={} label={} endDate={} today={}",
                    year.schoolId,
                    year.id,
                    year.label,
                    year.endDate,
                    today
                )
                academicYearRepository.save(year)
                    .flatMap { saved ->
                        outboxWriter.append(
                            schoolId = saved.schoolId,
                            module = "academic",
                            aggregateType = "AcademicYear",
                            aggregateId = saved.id!!,
                            eventType = "academic.year.closed",
                            payload = mapOf(
                                "academicYearId" to saved.id,
                                "label" to saved.label,
                                "reason" to "END_DATE_REACHED",
                                "endDate" to saved.endDate.toString()
                            )
                        ).thenReturn(saved)
                    }
            }
            .then()
    }

    private fun reconcileCurrent(schoolId: String, years: List<AcademicYear>, today: LocalDate): Mono<Void> {
        fun isLocked(year: AcademicYear) = AcademicMapper.yearIsLocked(year, today)
        fun isOperational(year: AcademicYear) =
            !today.isBefore(year.startDate) && today.isBefore(year.endDate) && !isLocked(year)

        val current = years.firstOrNull { it.current }
        val operational = years.filter { isOperational(it) }.maxByOrNull { it.startDate }

        // Courante encore écrite : ne pas écraser un choix manuel (ex. année future PLANNED).
        if (current != null && !isLocked(current)) {
            return Mono.empty()
        }

        // Courante verrouillée → promouvoir l’année opérationnelle s’il y en a une.
        if (operational != null && (current == null || current.id != operational.id)) {
            return clearCurrent(schoolId)
                .then(
                    Mono.defer {
                        operational.current = true
                        if (operational.status == AcademicYearStatus.PLANNED) {
                            operational.status = AcademicYearStatus.ACTIVE
                        }
                        log.info(
                            "academicYear.autoPromote schoolId={} yearId={} label={}",
                            schoolId,
                            operational.id,
                            operational.label
                        )
                        academicYearRepository.save(operational)
                    }
                )
                .then()
        }

        // Aucune courante et pas d’année opérationnelle : prendre la plus récente pour consultation.
        if (current == null) {
            val fallback = years.maxByOrNull { it.startDate } ?: return Mono.empty()
            fallback.current = true
            log.info(
                "academicYear.autoCurrent schoolId={} yearId={} label={} locked={}",
                schoolId,
                fallback.id,
                fallback.label,
                isLocked(fallback)
            )
            return academicYearRepository.save(fallback).then()
        }

        // Courante verrouillée sans successeur opérationnel : rester en consultation.
        return Mono.empty()
    }

    private fun assertWritableEntity(schoolId: String, year: AcademicYear): Mono<Void> =
        schoolToday(schoolId)
            .flatMap { today ->
                if (AcademicMapper.yearIsLocked(year, today)) {
                    Mono.error(
                        BusinessRuleException(
                            "YEAR_LOCKED",
                            "Academic year is closed; modifications are not allowed"
                        )
                    )
                } else {
                    Mono.empty()
                }
            }

    private fun mapYearResponse(year: AcademicYear): Mono<AcademicYearResponse> =
        schoolToday(year.schoolId).map { today -> AcademicMapper.toYearResponse(year, today) }

    private fun mapYearSummary(year: AcademicYear): Mono<AcademicYearSummaryDto> =
        schoolToday(year.schoolId).map { today -> AcademicMapper.toYearSummary(year, today) }

    private fun schoolToday(schoolId: String): Mono<LocalDate> =
        resolveTimezone(schoolId).map { zone -> LocalDate.now(zone) }

    private fun resolveTimezone(schoolId: String): Mono<ZoneId> =
        schoolRepository.findById(schoolId)
            .map { school ->
                try {
                    ZoneId.of(school.timezone.ifBlank { "Africa/Douala" })
                } catch (_: Exception) {
                    ZoneId.of("Africa/Douala")
                }
            }
            .defaultIfEmpty(ZoneId.of("Africa/Douala"))

    private fun clearCurrent(schoolId: String): Mono<Void> =
        academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
            .flatMap { current ->
                current.current = false
                academicYearRepository.save(current)
            }
            .then()

    private fun ensureLabelAvailable(schoolId: String, label: String, excludeId: String? = null): Mono<Void> =
        academicYearRepository.findBySchoolIdAndLabel(schoolId, label)
            .flatMap<Void> { existing ->
                if (excludeId != null && existing.id == excludeId) Mono.empty()
                else Mono.error(ConflictException("ACADEMIC_YEAR_LABEL_TAKEN", "Academic year label already exists"))
            }
            .switchIfEmpty(Mono.empty())

    private fun validateTerms(numbers: List<Int>) {
        if (numbers.toSet() != setOf(1, 2, 3)) {
            throw BusinessRuleException("TERMS_INVALID", "Academic year must contain terms 1, 2 and 3")
        }
    }
}
