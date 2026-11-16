package com.kobe.beesm.service.grading

import com.kobe.beesm.dto.grading.EntryProgressDto
import com.kobe.beesm.dto.grading.GradeSheetResponse
import com.kobe.beesm.dto.grading.SequenceMarkResponse
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.grading.SheetStatus
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.grading.GradeSheetRepository
import com.kobe.beesm.repository.grading.SequenceMarkRepository
import com.kobe.beesm.service.grading.api.GradeSheetApi
import com.kobe.beesm.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class GradeSheetService(
    private val gradeSheetRepository: GradeSheetRepository,
    private val sequenceMarkRepository: SequenceMarkRepository
) : GradeSheetApi {
    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<GradeSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
            .map { sheet ->
                if (!GradingAccess.canManageSheet(currentUser, sheet.teacherId) &&
                    !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
                ) {
                    throw ForbiddenException("FORBIDDEN", "Cannot read this grade sheet")
                }
                GradingMapper.toSheetResponse(sheet)
            }
    }

    fun listMineForSession(
        currentUser: CurrentUser,
        sessionId: String,
        schoolIdOverride: String? = null
    ): Flux<GradeSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            gradeSheetRepository.findBySchoolIdAndSessionId(schoolId, sessionId)
                .map { GradingMapper.toSheetResponse(it) }
        } else {
            val teacherId = GradingAccess.staffProfileId(currentUser)
                ?: return Flux.error(ForbiddenException("FORBIDDEN", "Missing staff profile"))
            gradeSheetRepository.findBySchoolIdAndTeacherIdAndSessionId(schoolId, teacherId, sessionId)
                .map { GradingMapper.toSheetResponse(it) }
        }
    }

    fun listSequenceMarks(
        currentUser: CurrentUser,
        sheetId: String,
        schoolIdOverride: String? = null
    ): Flux<SequenceMarkResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeSheetRepository.findByIdAndSchoolId(sheetId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
            .flatMapMany { sheet ->
                GradingAccess.assertCanManageSheet(currentUser, sheet.teacherId)
                sequenceMarkRepository.findBySchoolIdAndClassSubjectIdAndSessionId(
                    schoolId,
                    sheet.classSubjectId,
                    sheet.sessionId
                ).map { GradingMapper.toSequenceMarkResponse(it) }
            }
    }

    override fun entryProgressForSessions(
        schoolId: String,
        openSessionIds: List<String>,
        expectedPerSession: Long
    ): Mono<EntryProgressDto> {
        if (openSessionIds.isEmpty()) {
            return Mono.just(
                EntryProgressDto(
                    expectedSheets = 0,
                    draftSheets = 0,
                    submittedSheets = 0,
                    lockedSheets = 0,
                    existingSheets = 0
                )
            )
        }
        val expected = expectedPerSession.coerceAtLeast(0) * openSessionIds.size
        return Flux.fromIterable(openSessionIds)
            .flatMap { sessionId ->
                Mono.zip(
                    gradeSheetRepository
                        .countBySchoolIdAndSessionIdAndStatus(schoolId, sessionId, SheetStatus.DRAFT)
                        .defaultIfEmpty(0L),
                    gradeSheetRepository
                        .countBySchoolIdAndSessionIdAndStatus(schoolId, sessionId, SheetStatus.SUBMITTED)
                        .defaultIfEmpty(0L),
                    gradeSheetRepository
                        .countBySchoolIdAndSessionIdAndStatus(schoolId, sessionId, SheetStatus.LOCKED)
                        .defaultIfEmpty(0L)
                )
            }
            .reduce(Triple(0L, 0L, 0L)) { acc, tuple ->
                Triple(acc.first + tuple.t1, acc.second + tuple.t2, acc.third + tuple.t3)
            }
            .defaultIfEmpty(Triple(0L, 0L, 0L))
            .map { (draft, submitted, locked) ->
                EntryProgressDto(
                    expectedSheets = expected,
                    draftSheets = draft,
                    submittedSheets = submitted,
                    lockedSheets = locked,
                    existingSheets = draft + submitted + locked
                )
            }
    }
}
