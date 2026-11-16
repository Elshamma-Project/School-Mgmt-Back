package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.TimetableSubjectAliasResponse
import com.kobe.smels.dto.timetable.UpsertTimetableSubjectAliasRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.timetable.TimetableSubjectAlias
import com.kobe.smels.repository.timetable.TimetableSubjectAliasRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.text.Normalizer
import java.util.Locale

@Service
class TimetableSubjectAliasService(
    private val aliasRepository: TimetableSubjectAliasRepository,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi
) {
    fun upsert(
        currentUser: CurrentUser,
        request: UpsertTimetableSubjectAliasRequest
    ): Mono<TimetableSubjectAliasResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        val yearId = request.academicYearId.trim()
        val normalized = normalizeAlias(request.aliasNormalized)
        val sectionKey = sectionKeyOf(request.section)
        return assertCanManage(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, yearId))
            .then(
                aliasRepository.findBySchoolIdAndAcademicYearIdAndSectionKeyAndAliasNormalized(
                    schoolId,
                    yearId,
                    sectionKey,
                    normalized
                )
            )
            .flatMap { existing ->
                existing.section = request.section
                existing.sectionKey = sectionKey
                existing.aliasVariants = request.aliasVariants.map { it.trim() }.filter { it.isNotBlank() }
                existing.classSubjectIds = request.classSubjectIds.map { it.trim() }.filter { it.isNotBlank() }
                existing.unresolved = request.unresolved
                existing.note = request.note?.trim()?.ifBlank { null }
                existing.active = request.active
                aliasRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    aliasRepository.save(
                        TimetableSubjectAlias(
                            schoolId = schoolId,
                            academicYearId = yearId,
                            section = request.section,
                            sectionKey = sectionKey,
                            aliasNormalized = normalized,
                            aliasVariants = request.aliasVariants.map { it.trim() }.filter { it.isNotBlank() },
                            classSubjectIds = request.classSubjectIds.map { it.trim() }.filter { it.isNotBlank() },
                            unresolved = request.unresolved,
                            note = request.note?.trim()?.ifBlank { null },
                            active = request.active
                        )
                    )
                }
            )
            .onErrorMap(DuplicateKeyException::class.java) {
                ConflictException("ALIAS_EXISTS", "Alias already exists for this year and section")
            }
            .map { TimetableMapper.toAliasResponse(it) }
    }

    fun listByYear(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Flux<TimetableSubjectAliasResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return aliasRepository
            .findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            .map { TimetableMapper.toAliasResponse(it) }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableSubjectAliasResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return aliasRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ALIAS_NOT_FOUND", "Subject alias not found")))
            .map { TimetableMapper.toAliasResponse(it) }
    }

    fun deactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableSubjectAliasResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(aliasRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ALIAS_NOT_FOUND", "Subject alias not found")))
            .flatMap { alias ->
                academicYearApi.assertWritable(schoolId, alias.academicYearId).thenReturn(alias)
            }
            .flatMap { alias ->
                alias.active = false
                aliasRepository.save(alias)
            }
            .map { TimetableMapper.toAliasResponse(it) }
    }

    private fun assertCanManage(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        schoolBrandApi.getTimetableSettings(schoolId)
            .doOnNext { settings ->
                TimetableAccess.assertCanManage(currentUser, settings.timetableSurveillantCanEdit)
                TimetableAccess.logSuperAdmin(currentUser, schoolId, "timetable.alias.manage")
            }
            .then()

    companion object {
        fun sectionKeyOf(section: Section?): String = section?.name ?: "_"

        fun normalizeAlias(raw: String): String {
            val upper = raw.trim().uppercase(Locale.ROOT)
            val decomposed = Normalizer.normalize(upper, Normalizer.Form.NFD)
            return decomposed.replace("\\p{M}+".toRegex(), "")
        }
    }
}
