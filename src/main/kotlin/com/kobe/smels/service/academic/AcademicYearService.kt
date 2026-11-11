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
                ).thenReturn(AcademicMapper.toYearResponse(saved))
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
                ensureLabelAvailable(schoolId, label, excludeId = id)
                    .then(
                        Mono.defer {
                            year.label = label
                            year.startDate = request.startDate
                            year.endDate = request.endDate
                            year.status = request.status
                            year.terms = request.terms.map { AcademicMapper.toTerm(it) }
                            val prepare = if (request.current && !year.current) clearCurrent(schoolId) else Mono.empty()
                            year.current = request.current
                            prepare.then(academicYearRepository.save(year))
                        }
                    )
            }
            .map { AcademicMapper.toYearResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return academicYearRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .map { AcademicMapper.toYearResponse(it) }
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
        return ensureCurrentYear(schoolId)
            .then(
                academicYearRepository.findBySchoolId(schoolId, pageable)
                    .map { AcademicMapper.toYearResponse(it) }
                    .collectList()
                    .zipWith(academicYearRepository.countBySchoolId(schoolId))
            )
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
     * Définit manuellement l’année courante (ex. préparation de la rentrée suivante).
     * Réservé au directeur (+ SUPER_ADMIN).
     */
    fun setCurrent(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<AcademicYearResponse> {
        AcademicAccess.assertCanManageSchoolSettings(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return academicYearRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .flatMap { year ->
                if (year.current) {
                    Mono.just(year)
                } else {
                    clearCurrent(schoolId)
                        .then(
                            Mono.defer {
                                year.current = true
                                if (year.status == AcademicYearStatus.PLANNED) {
                                    year.status = AcademicYearStatus.ACTIVE
                                }
                                academicYearRepository.save(year)
                            }
                        )
                }
            }
            .map { AcademicMapper.toYearResponse(it) }
            .doOnNext {
                log.info(
                    "academicYear.setCurrent ok by={} schoolId={} yearId={} label={}",
                    currentUser.username,
                    schoolId,
                    it.id,
                    it.label
                )
            }
    }

    override fun getSummary(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto> =
        academicYearRepository.findByIdAndSchoolId(academicYearId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found")))
            .map { AcademicMapper.toYearSummary(it) }

    override fun findCurrent(schoolId: String): Mono<AcademicYearSummaryDto> =
        ensureCurrentYear(schoolId)
            .then(
                academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
                    .switchIfEmpty(
                        Mono.error(NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "No current academic year"))
                    )
            )
            .map { AcademicMapper.toYearSummary(it) }

    /**
     * Si aucune année n’est marquée courante, sélectionne automatiquement celle
     * dont l’intervalle contient la date du jour (timezone de l’école).
     */
    fun ensureCurrentYear(schoolId: String): Mono<Void> =
        academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
            .hasElement()
            .flatMap { hasCurrent ->
                if (hasCurrent) {
                    Mono.empty()
                } else {
                    resolveTimezone(schoolId)
                        .flatMap { zone ->
                            val today = LocalDate.now(zone)
                            academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId)
                                .filter { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
                                .next()
                                .flatMap { candidate ->
                                    log.info(
                                        "academicYear.autoCurrent schoolId={} yearId={} label={}",
                                        schoolId,
                                        candidate.id,
                                        candidate.label
                                    )
                                    candidate.current = true
                                    if (candidate.status == AcademicYearStatus.PLANNED) {
                                        candidate.status = AcademicYearStatus.ACTIVE
                                    }
                                    academicYearRepository.save(candidate)
                                }
                        }
                        .then()
                }
            }

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
