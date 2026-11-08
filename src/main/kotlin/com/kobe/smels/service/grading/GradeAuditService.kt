package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.GradeAuditEntryResponse
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

@Service
class GradeAuditService(
    private val gradeAuditEntryRepository: GradeAuditEntryRepository
) {
    fun list(
        currentUser: CurrentUser,
        page: Int = 0,
        size: Int = 50,
        schoolIdOverride: String? = null
    ): Flux<GradeAuditEntryResponse> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 200),
            Sort.by("changedAt").descending()
        )
        return gradeAuditEntryRepository.findBySchoolId(schoolId, pageable)
            .map { entry ->
                GradeAuditEntryResponse(
                    id = entry.id!!,
                    schoolId = entry.schoolId,
                    target = entry.target,
                    action = entry.action,
                    classSubjectId = entry.classSubjectId,
                    sessionId = entry.sessionId,
                    // Pas d'identifiant élève exposé dans les logs métier ; ici DTO admin seulement.
                    studentId = entry.studentId,
                    changedBy = entry.changedBy,
                    changedByRole = entry.changedByRole,
                    changedAt = entry.changedAt,
                    reason = entry.reason,
                    afterSessionClosed = entry.afterSessionClosed,
                    previousValue = entry.previousValue,
                    newValue = entry.newValue
                )
            }
    }
}
